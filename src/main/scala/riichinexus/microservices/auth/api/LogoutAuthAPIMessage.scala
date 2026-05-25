package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIWithTokenMessage, ApiPlanContext}
import riichinexus.microservices.auth.objects.apiTypes.ApiMessage
import upickle.default.*

final case class LogoutAuthAPIMessage() extends APIWithTokenMessage[ApiMessage] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ApiMessage] =
    for
      token <- IO(context.requireBearerToken)
      module = context.support.authModule
      loggedOutAt <- IO.realTimeInstant
      _ <- IO {
        module.transactionManager.inTransaction {
          logout(context, token, loggedOutAt)
        }
      }
    yield ApiMessage("Logged out")

  private def logout(context: ApiPlanContext, token: String, loggedOutAt: Instant): Unit =
    val module = context.support.authModule
    module.authenticatedSessionRepository.findByToken(token).foreach { session =>
      if session.canAuthenticate(loggedOutAt) then
        module.authenticatedSessionRepository.save(session.revoke(loggedOutAt))
    }
