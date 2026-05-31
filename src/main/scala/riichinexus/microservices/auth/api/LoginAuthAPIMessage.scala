package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.AuthModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.domain.service.AuthenticationFailure
import riichinexus.microservices.auth.objects.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.objects.apiTypes.{AuthSuccessResponse, CurrentSessionRoleFlags}
import riichinexus.microservices.auth.security.AuthPasswordHasher
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class LoginAuthAPIMessage(
    username: String,
    password: String
) extends APIMessage[AuthSuccessResponse] derives ReadWriter:

  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessResponse] =
    for
      loginAt <- IO.realTimeInstant
      module = context.support.authModule
      command = LoginCommand(AccountCredential.normalizeUsername(username), password, loginAt)
      result <- IO.blocking {
        module.transactionManager.inTransaction {
          login(context, module, command)
        }
      }
    yield AuthSuccessResponse(
      userId = result.player.id.value,
      username = result.credential.username,
      displayName = result.player.nickname,
      token = result.session.token,
      roles = context.support.registeredRoleFlags(result.player)
    )

  private def login(context: ApiPlanContext, module: AuthModuleContext, command: LoginCommand): LoginResult =
    val connection = context.connection
    require(command.password.nonEmpty, "Password is required")
    val credential = AccountCredentialTable.findByUsername(connection, command.username)
      .getOrElse(throw AuthenticationFailure("Invalid username or password", "invalid_credentials"))
    if !AuthPasswordHasher.verify(command.password, credential) then
      throw AuthenticationFailure("Invalid username or password", "invalid_credentials")

    val player = GetPlayerAPIMessage.findPlayer(context.connection, credential.playerId)
      .getOrElse(throw AuthenticationFailure(s"Player ${credential.playerId.value} was not found", "invalid_credentials"))
    ensureActivePlayer(player)

    val session = AuthenticatedSessionTable.save(
      connection,
      AuthenticatedSession.create(
        username = credential.username,
        playerId = credential.playerId,
        createdAt = command.loginAt,
        ttl = SessionTtl
      )
    )
    LoginResult(credential, player, session)

  private def ensureActivePlayer(player: Player): Unit =
    if player.status != PlayerStatus.Active then
      throw AuthenticationFailure(
        s"Player ${player.id.value} is not active",
        "inactive_account"
      )

  private final case class LoginCommand(
      username: String,
      password: String,
      loginAt: Instant
  )

  private final case class LoginResult(
      credential: AccountCredential,
      player: Player,
      session: AuthenticatedSession
  )
