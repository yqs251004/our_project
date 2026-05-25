package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.AuthModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.objects.apiTypes.AuthSuccessResponse
import riichinexus.microservices.auth.security.AuthPasswordHasher
import upickle.default.*

final case class RegisterAuthAPIMessage(
    username: String,
    password: String,
    displayName: String
) extends APIMessage[AuthSuccessResponse] derives ReadWriter:

  private val DefaultRank = RankSnapshot(RankPlatform.Custom, "Unranked")
  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessResponse] =
    for
      registeredAt <- IO.realTimeInstant
      module = context.support.authModule
      command = RegisterAuthCommand(
        username = AccountCredential.normalizeUsername(username),
        password = password,
        displayName = normalizeDisplayName(displayName),
        registeredAt = registeredAt
      )
      result <- IO {
        module.transactionManager.inTransaction {
          register(module, command)
        }
      }
    yield riichinexus.microservices.auth.objects.apiTypes.AuthSuccessView(
      userId = result.player.id.value,
      username = result.username,
      displayName = result.player.nickname,
      token = result.session.token,
      roles = context.support.registeredRoleFlags(result.player)
    )

  private def register(module: AuthModuleContext, command: RegisterAuthCommand): RegisterAuthResult =
    validatePassword(command.password)
    if module.accountCredentialRepository.findByUsername(command.username).nonEmpty then
      throw IllegalArgumentException(s"Username ${command.username} is already registered")

    val player = resolveRegisteredPlayer(module, command)
    ensureActivePlayer(player)
    saveCredential(module, command, player)
    val session = module.authenticatedSessionRepository.save(
      AuthenticatedSession.create(
        username = command.username,
        playerId = player.id,
        createdAt = command.registeredAt,
        ttl = SessionTtl
      )
    )
    RegisterAuthResult(command.username, player, session)

  private def resolveRegisteredPlayer(
      module: AuthModuleContext,
      command: RegisterAuthCommand
  ): Player =
    module.playerRepository.findAll().find(_.userId.equalsIgnoreCase(command.username)) match
      case Some(existing) if existing.nickname == command.displayName =>
        existing
      case Some(existing) =>
        module.playerRepository.save(existing.copy(nickname = command.displayName))
      case None =>
        module.playerRegistration.registerPlayer(
          userId = command.username,
          nickname = command.displayName,
          rank = DefaultRank,
          registeredAt = command.registeredAt
        )

  private def saveCredential(
      module: AuthModuleContext,
      command: RegisterAuthCommand,
      player: Player
  ): AccountCredential =
    val passwordDigest = AuthPasswordHasher.hash(command.password)
    module.accountCredentialRepository.save(
      AccountCredential(
        username = command.username,
        playerId = player.id,
        passwordHash = passwordDigest.hash,
        passwordSalt = passwordDigest.salt,
        passwordIterations = passwordDigest.iterations,
        createdAt = command.registeredAt,
        updatedAt = command.registeredAt
      )
    )

  private def normalizeDisplayName(displayName: String): String =
    Option(displayName)
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw IllegalArgumentException("Display name is required"))

  private def validatePassword(password: String): Unit =
    require(password.length >= 8, "Password must be at least 8 characters")

  private def ensureActivePlayer(player: Player): Unit =
    if player.status != PlayerStatus.Active then
      throw riichinexus.domain.service.AuthenticationFailure(
        s"Player ${player.id.value} is not active",
        "inactive_account"
      )

  private final case class RegisterAuthCommand(
      username: String,
      password: String,
      displayName: String,
      registeredAt: Instant
  )

  private final case class RegisterAuthResult(
      username: String,
      player: Player,
      session: AuthenticatedSession
  )
