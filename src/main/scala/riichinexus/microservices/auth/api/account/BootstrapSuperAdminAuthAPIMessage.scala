package riichinexus.microservices.auth.api.account

import cats.effect.IO
import riichinexus.microservices.auth.domain.account.model.AuthenticationFailure
import riichinexus.microservices.auth.domain.account.model.SuperAdminBootstrapErrorCode
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.domain.account.functions.AccountCredentialFunctions
import riichinexus.microservices.auth.domain.session.functions.AuthenticatedSessionFunctions
import riichinexus.microservices.auth.domain.account.functions.PasswordHashFunctions
import riichinexus.microservices.auth.domain.account.model.AccountCredential
import riichinexus.microservices.auth.domain.session.model.AuthenticatedSession
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.auth.objects.account.apiTypes.BootstrapSuperAdminRequest
import riichinexus.microservices.auth.objects.session.AuthSuccessView
import riichinexus.microservices.auth.objects.session.CurrentSessionRoleFlags
import riichinexus.microservices.auth.security.PasswordSaltGenerator
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import riichinexus.microservices.player.api.`private`.ListAllPlayersPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerNicknameUpdatePrivateAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerSuperAdminGrantPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerByUserIdPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import riichinexus.microservices.player.api.CreatePlayerAPIMessage
import riichinexus.microservices.player.objects.apiTypes.CreatePlayerRequest
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import java.time.Instant
import java.util.NoSuchElementException

/** 初始化系统超级管理员账号。 */
final case class BootstrapSuperAdminAuthAPIMessage(
    request: BootstrapSuperAdminRequest
) extends APIMessage[AuthSuccessView]:

  private val DefaultRank = RankSnapshot(RankPlatform.Custom, "Unranked")
  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessView] =
    for
      initializedAt <- IO.realTimeInstant
      bootstrapKey <- IO.blocking(normalizeBootstrapKey(request.bootstrapKey))
      normalizedUsername <- IO.blocking(normalizeBootstrapUsername(request.username))
      normalizedDisplayName <- IO.blocking(normalizeDisplayName(request.displayName))
      configuredKey <- loadConfiguredBootstrapKey
      _ <- validateBootstrapRequest(bootstrapKey, request.password, configuredKey)
      _ <- ensureUsernameAvailable(context, normalizedUsername)
      _ <- ensureNoSuperAdmin(context)
      player <- resolveRegisteredPlayer(context, normalizedUsername, normalizedDisplayName)
      _ <- IO.blocking(ensureActivePlayer(player))
      _ <- saveCredential(context, normalizedUsername, request.password, initializedAt, player)
      superAdminPlayer <- grantSuperAdmin(context, initializedAt, player)
      session <- createSession(context, normalizedUsername, initializedAt, superAdminPlayer)
    yield authSuccessView(normalizedUsername, superAdminPlayer, session)

  private def loadConfiguredBootstrapKey: IO[String] =
    IO.blocking(configuredBootstrapKey)

  private def validateBootstrapRequest(
      bootstrapKey: String,
      password: String,
      configuredKey: String
  ): IO[Unit] =
    IO.blocking {
      validateBootstrapKey(bootstrapKey, configuredKey)
      validatePassword(password)
    }

  private def ensureUsernameAvailable(
      context: ApiPlanContext,
      username: String
  ): IO[Unit] =
    IO.blocking {
      if AccountCredentialTable.findByUsername(context.connection, username).nonEmpty then
        throw AuthenticationFailure(
          "Username " + username + " is already registered",
          SuperAdminBootstrapErrorCode.toString(
            SuperAdminBootstrapErrorCode.UsernameAlreadyRegistered
          )
        )
    }

  private def resolveRegisteredPlayer(
      context: ApiPlanContext,
      username: String,
      displayName: String
  ): IO[PlayerPrivateView] =
    ListAllPlayersPrivateAPIMessage()
      .plan(context)
      .flatMap { players =>
        players.find(_.userId.equalsIgnoreCase(username)) match
          case Some(existing) if existing.nickname == displayName =>
            IO.pure(existing)
          case Some(existing) =>
            RecordPlayerNicknameUpdatePrivateAPIMessage(existing.id, displayName)
              .plan(context)
              .flatMap(_ =>
                ResolvePlayerPrivateAPIMessage(existing.id).plan(context).map(
                  _.getOrElse(throw NoSuchElementException(s"Player ${existing.id.value} was not found"))
                )
              )
          case None =>
            createPlayerViaPublicAPI(context, username, displayName)
      }

  private def createPlayerViaPublicAPI(
      context: ApiPlanContext,
      username: String,
      displayName: String
  ): IO[PlayerPrivateView] =
    for
      _ <- CreatePlayerAPIMessage(
        CreatePlayerRequest(
          userId = username,
          nickname = displayName,
          rankPlatform = RankPlatform.toString(DefaultRank.platform),
          tier = DefaultRank.tier,
          stars = DefaultRank.stars,
          initialElo = 1500
        )
      ).plan(context)
      player <- ResolvePlayerByUserIdPrivateAPIMessage(username).plan(context).map(
        _.getOrElse(throw IllegalStateException(s"Player ${username} was not created"))
      )
    yield player

  private def saveCredential(
      context: ApiPlanContext,
      username: String,
      password: String,
      initializedAt: Instant,
      player: PlayerPrivateView
  ): IO[AccountCredential] =
    IO.blocking {
      val passwordDigest = PasswordHashFunctions.digest(password, PasswordSaltGenerator.nextSalt())
      AccountCredentialTable.save(
        context.connection,
        AccountCredential(
          username = username,
          playerId = player.id,
          passwordHash = passwordDigest.hash,
          passwordSalt = passwordDigest.salt,
          passwordIterations = passwordDigest.iterations,
          createdAt = initializedAt,
          updatedAt = initializedAt
        )
      )
    }

  private def grantSuperAdmin(
      context: ApiPlanContext,
      initializedAt: Instant,
      player: PlayerPrivateView
  ): IO[PlayerPrivateView] =
    RecordPlayerSuperAdminGrantPrivateAPIMessage(player.id, initializedAt, None)
      .plan(context)
      .flatMap(_ =>
        ResolvePlayerPrivateAPIMessage(player.id).plan(context).map(
          _.getOrElse(throw NoSuchElementException(s"Player ${player.id.value} was not found"))
        )
      )

  private def createSession(
      context: ApiPlanContext,
      username: String,
      initializedAt: Instant,
      player: PlayerPrivateView
  ): IO[AuthenticatedSession] =
    IO.blocking(
      AuthenticatedSessionTable.save(
        context.connection,
        AuthenticatedSessionFunctions.create(
          username = username,
          playerId = player.id,
          createdAt = initializedAt,
          ttl = SessionTtl
        )
      )
    )

  private def configuredBootstrapKey: String =
    sys.env
      .get("RIICHI_SUPERADMIN_BOOTSTRAP_KEY")
      .orElse(sys.env.get("SUPERADMIN_BOOTSTRAP_KEY"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(
        throw AuthorizationFailure(
          "Super admin bootstrap key is not configured",
          SuperAdminBootstrapErrorCode.toString(
            SuperAdminBootstrapErrorCode.BootstrapKeyNotConfigured
          )
        )
      )

  private def validateBootstrapKey(value: String, configuredKey: String): Unit =
    if value != configuredKey then
      throw AuthenticationFailure(
        "Invalid super admin bootstrap key",
        SuperAdminBootstrapErrorCode.toString(
          SuperAdminBootstrapErrorCode.InvalidBootstrapKey
        )
      )

  private def normalizeBootstrapUsername(username: String): String =
    try AccountCredentialFunctions.normalizeUsername(username)
    catch
      case _: IllegalArgumentException =>
        throw AuthenticationFailure(
          "Username is required",
          SuperAdminBootstrapErrorCode.toString(
            SuperAdminBootstrapErrorCode.UsernameRequired
          )
        )

  private def normalizeBootstrapKey(value: String): String =
    Option(value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(
        throw AuthenticationFailure(
          "Invalid super admin bootstrap key",
          SuperAdminBootstrapErrorCode.toString(
            SuperAdminBootstrapErrorCode.InvalidBootstrapKey
          )
        )
      )

  private def ensureNoSuperAdmin(context: ApiPlanContext): IO[Unit] =
    ListAllPlayersPrivateAPIMessage().plan(context).map { players =>
      if players.exists(_.roleGrants.exists(_.role == Role.SuperAdmin)) then
        throw AuthorizationFailure(
          "Super admin has already been initialized",
          SuperAdminBootstrapErrorCode.toString(
            SuperAdminBootstrapErrorCode.AlreadyInitialized
          )
        )
    }

  private def normalizeDisplayName(displayName: String): String =
    Option(displayName)
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(
        throw AuthenticationFailure(
          "Display name is required",
          SuperAdminBootstrapErrorCode.toString(
            SuperAdminBootstrapErrorCode.DisplayNameRequired
          )
        )
      )

  private def validatePassword(password: String): Unit =
    if password.length < 8 then
      throw AuthenticationFailure(
        "Password must be at least 8 characters",
        SuperAdminBootstrapErrorCode.toString(
          SuperAdminBootstrapErrorCode.PasswordTooShort
        )
      )

  private def ensureActivePlayer(player: PlayerPrivateView): Unit =
    if !player.active then
      throw AuthenticationFailure(
        "Player " + player.id.value + " is not active",
        "inactive_account"
      )

  private def registeredRoleFlags(player: PlayerPrivateView): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )

  private def authSuccessView(
      username: String,
      player: PlayerPrivateView,
      session: AuthenticatedSession
  ): AuthSuccessView =
    AuthSuccessView(
      userId = player.id.value,
      username = username,
      displayName = player.nickname,
      token = session.token,
      roles = registeredRoleFlags(player)
    )

end BootstrapSuperAdminAuthAPIMessage
