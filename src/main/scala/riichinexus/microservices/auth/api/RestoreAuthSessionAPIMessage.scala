package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIWithTokenMessage, ApiPlanContext}
import riichinexus.bootstrap.AuthModuleContext
import riichinexus.domain.model.PlayerStatus
import riichinexus.domain.model.{AuthenticatedSession, Player}
import riichinexus.domain.service.AuthenticationFailure
import riichinexus.microservices.auth.objects.apiTypes.AuthSessionResponse
import upickle.default.*

final case class RestoreAuthSessionAPIMessage() extends APIWithTokenMessage[AuthSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AuthSessionResponse] =
    for
      token <- IO(context.requireBearerToken)
      module = context.support.authModule
      asOf <- IO.realTimeInstant
      command = RestoreSessionCommand(token, asOf)
      result <- IO {
        module.transactionManager.inTransaction {
          restoreSession(module, command)
        }
      }
    yield riichinexus.microservices.auth.objects.apiTypes.AuthSessionView(
      userId = result.player.id.value,
      username = result.session.username,
      displayName = result.player.nickname,
      authenticated = true,
      roles = context.support.registeredRoleFlags(result.player)
    )

  private def restoreSession(
      module: AuthModuleContext,
      command: RestoreSessionCommand
  ): RestoreSessionResult =
    val session = module.authenticatedSessionRepository.findByToken(command.token)
      .getOrElse(throw AuthenticationFailure("Session is invalid or expired", "invalid_session"))
    if !session.canAuthenticate(command.asOf) then
      throw AuthenticationFailure("Session is invalid or expired", "invalid_session")

    val touched = module.authenticatedSessionRepository.save(session.touch(command.asOf))
    val player = module.playerTable.find(touched.playerId)
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
