package riichinexus.microservices.auth.api
import riichinexus.api.functions.ApiPlanContextFunctions
import riichinexus.microservices.auth.api.`private`.CurrentSessionViewResolver

import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import riichinexus.api.{APIWithTokenMessage, ApiPlanContext}
import riichinexus.bootstrap.AuthModuleContext
import riichinexus.domain.service.AuthenticationFailure
import riichinexus.microservices.auth.domain.functions.AuthenticatedSessionFunctions
import riichinexus.microservices.auth.domain.model.AuthenticatedSession
import riichinexus.microservices.auth.objects.apiTypes.AuthSessionView
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class RestoreAuthSessionAPIMessage() extends APIWithTokenMessage[AuthSessionView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AuthSessionView] =
    for
      token <- IO.blocking(ApiPlanContextFunctions.requireBearerToken(context))
      module = context.support.authModule
      asOf <- IO.realTimeInstant
      command = RestoreSessionCommand(token, asOf)
      result <- IO.blocking {
        module.transactionManager.inTransaction {
          restoreSession(context, module, command)
        }
      }
    yield AuthSessionView(
      userId = result.player.id.value,
      username = result.session.username,
      displayName = result.player.nickname,
      authenticated = true,
      roles = CurrentSessionViewResolver.registeredRoleFlags(result.player)
    )

  private def restoreSession(
      context: ApiPlanContext,
      module: AuthModuleContext,
      command: RestoreSessionCommand
  ): RestoreSessionResult =
    val connection = context.connection
    val session = AuthenticatedSessionTable.findByToken(connection, command.token)
      .getOrElse(throw AuthenticationFailure("Session is invalid or expired", "invalid_session"))
    if !AuthenticatedSessionFunctions.canAuthenticate(session, command.asOf) then
      throw AuthenticationFailure("Session is invalid or expired", "invalid_session")

    val touched = AuthenticatedSessionTable.save(connection, AuthenticatedSessionFunctions.touch(session, command.asOf))
    val player = GetPlayerAPIMessage.findPlayer(context.connection, touched.playerId)
      .getOrElse(throw AuthenticationFailure(s"Player ${touched.playerId.value} was not found", "invalid_session"))
    if player.status != PlayerStatus.Active then
      throw AuthenticationFailure(s"Player ${player.id.value} is not active", "inactive_account")
    RestoreSessionResult(touched, player)

  private final case class RestoreSessionCommand(
      token: String,
      asOf: Instant
  )

  private final case class RestoreSessionResult(
      session: AuthenticatedSession,
      player: Player
  )
