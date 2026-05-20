package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIWithTokenMessage, ApiPlanContext}
import riichinexus.microservices.auth.objects.apiTypes.ApiMessage
import upickle.default.*

final case class LogoutAuthAPIMessage() extends APIWithTokenMessage[ApiMessage] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ApiMessage] =
    IO {
      val module = context.support.authModule
      module.transactionManager.inTransaction {
        val loggedOutAt = Instant.now()
        module.authenticatedSessionRepository.findByToken(context.requireBearerToken).foreach { session =>
          if session.canAuthenticate(loggedOutAt) then
            module.authenticatedSessionRepository.save(session.revoke(loggedOutAt))
        }
      }
      ApiMessage("Logged out")
    }
