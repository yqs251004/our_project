package riichinexus.application.service

import java.security.SecureRandom
import java.time.{Duration, Instant}
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthenticationFailure

private object PasswordHasher:
  private val Algorithm = "PBKDF2WithHmacSHA256"
  private val KeyLengthBits = 256
  private val DefaultIterations = 65_536
  private val SaltLength = 16
  private val random = SecureRandom()

  final case class PasswordDigest(
      hash: String,
      salt: String,
      iterations: Int
  )

  def hash(password: String): PasswordDigest =
    val saltBytes = new Array[Byte](SaltLength)
    random.nextBytes(saltBytes)
    val iterations = DefaultIterations
    PasswordDigest(
      hash = encode(derive(password, saltBytes, iterations)),
      salt = encode(saltBytes),
      iterations = iterations
    )

  def verify(
      password: String,
      credential: AccountCredential
  ): Boolean =
    val derived = derive(password, Base64.getDecoder.decode(credential.passwordSalt), credential.passwordIterations)
    encode(derived) == credential.passwordHash

  private def derive(password: String, salt: Array[Byte], iterations: Int): Array[Byte] =
    val spec = PBEKeySpec(password.toCharArray, salt, iterations, KeyLengthBits)
    try SecretKeyFactory.getInstance(Algorithm).generateSecret(spec).getEncoded
    finally spec.clearPassword()

  private def encode(bytes: Array[Byte]): String =
    Base64.getEncoder.encodeToString(bytes)

final class AuthApplicationService(
    playerService: PlayerApplicationService,
    playerRepository: PlayerRepository,
    accountCredentialRepository: AccountCredentialRepository,
    authenticatedSessionRepository: AuthenticatedSessionRepository,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  private val DefaultRank = RankSnapshot(RankPlatform.Custom, "Unranked")
  private val SessionTtl = Duration.ofDays(30)

  def register(
      username: String,
      password: String,
      displayName: String,
      registeredAt: Instant = Instant.now()
  ): AuthSuccessView =
    transactionManager.inTransaction {
      val normalizedUsername = AccountCredential.normalizeUsername(username)
      val normalizedDisplayName = normalizeDisplayName(displayName)
      validatePassword(password)

      if accountCredentialRepository.findByUsername(normalizedUsername).nonEmpty then
        throw IllegalArgumentException(s"Username $normalizedUsername is already registered")

      val player = upsertBoundPlayer(normalizedUsername, normalizedDisplayName, registeredAt)
      ensureActivePlayer(player)

      val passwordDigest = PasswordHasher.hash(password)
      accountCredentialRepository.save(
        AccountCredential(
          username = normalizedUsername,
          playerId = player.id,
          passwordHash = passwordDigest.hash,
          passwordSalt = passwordDigest.salt,
          passwordIterations = passwordDigest.iterations,
          createdAt = registeredAt,
          updatedAt = registeredAt
        )
      )

      val session = authenticatedSessionRepository.save(
        AuthenticatedSession.create(
          username = normalizedUsername,
          playerId = player.id,
          createdAt = registeredAt,
          ttl = SessionTtl
        )
      )

      successView(player, normalizedUsername, session.token)
    }

  def login(
      username: String,
      password: String,
      loginAt: Instant = Instant.now()
  ): AuthSuccessView =
    transactionManager.inTransaction {
      val normalizedUsername = AccountCredential.normalizeUsername(username)
      require(password.nonEmpty, "Password is required")

      val credential = accountCredentialRepository.findByUsername(normalizedUsername)
        .getOrElse(throw AuthenticationFailure("Invalid username or password", "invalid_credentials"))

      if !PasswordHasher.verify(password, credential) then
        throw AuthenticationFailure("Invalid username or password", "invalid_credentials")

      val player = playerRepository.findById(credential.playerId)
        .getOrElse(throw AuthenticationFailure(s"Player ${credential.playerId.value} was not found", "invalid_credentials"))
      ensureActivePlayer(player)

      val session = authenticatedSessionRepository.save(
        AuthenticatedSession.create(
          username = credential.username,
          playerId = credential.playerId,
          createdAt = loginAt,
          ttl = SessionTtl
        )
      )

      successView(player, credential.username, session.token)
    }

  def restoreSession(
      token: String,
      asOf: Instant = Instant.now()
  ): AuthSessionView =
    transactionManager.inTransaction {
      val session = requireActiveSession(token, asOf)
      val touched = authenticatedSessionRepository.save(session.touch(asOf))
      val player = playerRepository.findById(touched.playerId)
        .getOrElse(throw AuthenticationFailure(s"Player ${touched.playerId.value} was not found", "invalid_session"))
      ensureActivePlayer(player)
      AuthSessionView(
        userId = player.id,
        username = touched.username,
        displayName = player.nickname,
        authenticated = true,
        roles = roleFlags(player)
      )
    }

  def logout(
      token: String,
      loggedOutAt: Instant = Instant.now()
  ): Unit =
    transactionManager.inTransaction {
      normalizeToken(token).flatMap(authenticatedSessionRepository.findByToken).foreach { session =>
        if session.canAuthenticate(loggedOutAt) then
          authenticatedSessionRepository.save(session.revoke(loggedOutAt))
      }
    }

  private def upsertBoundPlayer(
      normalizedUsername: String,
      displayName: String,
      registeredAt: Instant
  ): Player =
    playerRepository.findAll().find(_.userId.equalsIgnoreCase(normalizedUsername)) match
      case Some(existing) if existing.nickname == displayName =>
        existing
      case Some(existing) =>
        playerRepository.save(existing.copy(nickname = displayName))
      case None =>
        playerService.registerPlayer(
          userId = normalizedUsername,
          nickname = displayName,
          rank = DefaultRank,
          registeredAt = registeredAt
        )

  private def requireActiveSession(
      token: String,
      asOf: Instant
  ): AuthenticatedSession =
    val normalizedToken = normalizeToken(token)
      .getOrElse(throw AuthenticationFailure("Bearer token is required", "missing_token"))
    val session = authenticatedSessionRepository.findByToken(normalizedToken)
      .getOrElse(throw AuthenticationFailure("Session is invalid or expired", "invalid_session"))
    if !session.canAuthenticate(asOf) then
      throw AuthenticationFailure("Session is invalid or expired", "invalid_session")
    session

  private def normalizeDisplayName(displayName: String): String =
    Option(displayName)
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw IllegalArgumentException("Display name is required"))

  private def normalizeToken(token: String): Option[String] =
    Option(token).map(_.trim).filter(_.nonEmpty)

  private def validatePassword(password: String): Unit =
    require(password.length >= 8, "Password must be at least 8 characters")

  private def ensureActivePlayer(player: Player): Unit =
    if player.status != PlayerStatus.Active then
      throw AuthenticationFailure(
        s"Player ${player.id.value} is not active",
        "inactive_account"
      )

  private def roleFlags(player: Player): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == RoleKind.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == RoleKind.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin)
    )

  private def successView(
      player: Player,
      username: String,
      token: String
  ): AuthSuccessView =
    AuthSuccessView(
      userId = player.id,
      username = username,
      displayName = player.nickname,
      token = token,
      roles = roleFlags(player)
    )
