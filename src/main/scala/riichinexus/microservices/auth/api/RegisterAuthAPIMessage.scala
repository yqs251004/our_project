package riichinexus.microservices.auth.api
import riichinexus.microservices.auth.api.`private`.CurrentSessionViewResolver

import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.AuthModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.functions.{AccountCredentialFunctions, AuthenticatedSessionFunctions, PasswordHashFunctions}
import riichinexus.microservices.auth.domain.model.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.objects.apiTypes.AuthSuccessView
import riichinexus.microservices.auth.security.PasswordSaltGenerator
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class RegisterAuthAPIMessage(
    username: String,
    password: String,
    displayName: String
) extends APIMessage[AuthSuccessView] derives ReadWriter:

  private val DefaultRank = RankSnapshot(RankPlatform.Custom, "Unranked")
  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessView] =
    for
      registeredAt <- IO.realTimeInstant
      module = context.support.authModule
      command = RegisterAuthCommand(
        username = AccountCredentialFunctions.normalizeUsername(username),
        password = password,
        displayName = normalizeDisplayName(displayName),
        registeredAt = registeredAt
      )
      result <- IO.blocking {
        module.transactionManager.inTransaction {
          register(context, module, command)
        }
      }
    yield AuthSuccessView(
      userId = result.player.id.value,
      username = result.username,
      displayName = result.player.nickname,
      token = result.session.token,
      roles = CurrentSessionViewResolver.registeredRoleFlags(result.player)
    )

  private def register(context: ApiPlanContext, module: AuthModuleContext, command: RegisterAuthCommand): RegisterAuthResult =
    val connection = context.connection
    validatePassword(command.password)
    if AccountCredentialTable.findByUsername(connection, command.username).nonEmpty then
      throw IllegalArgumentException(s"Username ${command.username} is already registered")

    val player = resolveRegisteredPlayer(context, command)
    ensureActivePlayer(player)
    saveCredential(connection, command, player)
    val session = AuthenticatedSessionTable.save(
      connection,
      AuthenticatedSessionFunctions.create(
        username = command.username,
        playerId = player.id,
        createdAt = command.registeredAt,
        ttl = SessionTtl
      )
    )
    RegisterAuthResult(command.username, player, session)

  private def resolveRegisteredPlayer(
      context: ApiPlanContext,
      command: RegisterAuthCommand
  ): Player =
    ListPlayersAPIMessage.findAllPlayers(context.connection).find(_.userId.equalsIgnoreCase(command.username)) match
      case Some(existing) if existing.nickname == command.displayName =>
        existing
      case Some(existing) =>
        CreatePlayerAPIMessage.persistPlayer(context.connection, existing.copy(nickname = command.displayName))
      case None =>
        CreatePlayerAPIMessage.createPlayer(
          connection = context.connection,
          userId = command.username,
          nickname = command.displayName,
          rank = DefaultRank,
          registeredAt = command.registeredAt,
          initialElo = 1500
        )

  private def saveCredential(
      connection: java.sql.Connection,
      command: RegisterAuthCommand,
      player: Player
  ): AccountCredential =
    val passwordDigest = PasswordHashFunctions.digest(command.password, PasswordSaltGenerator.nextSalt())
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
