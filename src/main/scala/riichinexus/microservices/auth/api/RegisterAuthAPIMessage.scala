package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.AuthModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.objects.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.objects.apiTypes.AuthSuccessResponse
import riichinexus.microservices.auth.security.AuthPasswordHasher
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import riichinexus.microservices.player.tables.player.PlayerTable
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
          register(context.connection, module, command)
        }
      }
    yield AuthSuccessResponse.fromView(
      riichinexus.microservices.auth.objects.AuthSuccessView(
        userId = result.player.id.value,
        username = result.username,
        displayName = result.player.nickname,
        token = result.session.token,
        roles = context.support.registeredRoleFlags(result.player)
      )
    )

  private def register(connection: java.sql.Connection, module: AuthModuleContext, command: RegisterAuthCommand): RegisterAuthResult =
    validatePassword(command.password)
    if AccountCredentialTable.findByUsername(connection, command.username).nonEmpty then
      throw IllegalArgumentException(s"Username ${command.username} is already registered")

    val player = resolveRegisteredPlayer(connection, module, command)
    ensureActivePlayer(player)
    saveCredential(connection, command, player)
    val session = AuthenticatedSessionTable.save(
      connection,
      AuthenticatedSession.create(
        username = command.username,
        playerId = player.id,
        createdAt = command.registeredAt,
        ttl = SessionTtl
      )
    )
    RegisterAuthResult(command.username, player, session)

  private def resolveRegisteredPlayer(
      connection: java.sql.Connection,
      module: AuthModuleContext,
      command: RegisterAuthCommand
  ): Player =
    PlayerTable.findAll(connection).find(_.userId.equalsIgnoreCase(command.username)) match
      case Some(existing) if existing.nickname == command.displayName =>
        existing
      case Some(existing) =>
        PlayerTable.save(connection, existing.copy(nickname = command.displayName))
      case None =>
        module.playerRegistration.registerPlayer(
          connection,
          command.username,
          command.displayName,
          DefaultRank,
          command.registeredAt,
          initialElo = 1500
        )

  private def saveCredential(
      connection: java.sql.Connection,
      command: RegisterAuthCommand,
      player: Player
  ): AccountCredential =
    val passwordDigest = AuthPasswordHasher.hash(command.password)
    AccountCredentialTable.save(
      connection,
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
