package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIWithTokenMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerStatus
import riichinexus.domain.service.AuthenticationFailure
import riichinexus.microservices.auth.objects.apiTypes.AuthSessionResponse
import upickle.default.*

final case class RestoreAuthSessionAPIMessage() extends APIWithTokenMessage[AuthSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AuthSessionResponse] =
    IO {
      val module = context.support.authModule
      module.transactionManager.inTransaction {
        val asOf = Instant.now()
        val token = context.requireBearerToken
        val session = module.authenticatedSessionRepository.findByToken(token)
          .getOrElse(throw AuthenticationFailure("Session is invalid or expired", "invalid_session"))
        if !session.canAuthenticate(asOf) then
          throw AuthenticationFailure("Session is invalid or expired", "invalid_session")

        val touched = module.authenticatedSessionRepository.save(session.touch(asOf))
        val player = module.playerTable.find(touched.playerId)
          .getOrElse(throw AuthenticationFailure(s"Player ${touched.playerId.value} was not found", "invalid_session"))
        if player.status != PlayerStatus.Active then
          throw AuthenticationFailure(s"Player ${player.id.value} is not active", "inactive_account")

        riichinexus.microservices.auth.objects.apiTypes.AuthSessionView(
          userId = player.id,
          username = touched.username,
          displayName = player.nickname,
          authenticated = true,
          roles = context.support.registeredRoleFlags(player)
        )
      }
    }
