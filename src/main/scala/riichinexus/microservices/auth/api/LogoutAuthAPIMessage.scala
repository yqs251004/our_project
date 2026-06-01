package riichinexus.microservices.auth.api
import riichinexus.api.functions.ApiPlanContextFunctions

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIWithTokenMessage, ApiPlanContext}
import riichinexus.microservices.auth.domain.functions.AuthenticatedSessionFunctions
import riichinexus.microservices.auth.objects.apiTypes.LogoutResponse
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import upickle.default.*

final case class LogoutAuthAPIMessage() extends APIWithTokenMessage[LogoutResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[LogoutResponse] =
    for
      token <- IO.blocking(ApiPlanContextFunctions.requireBearerToken(context))
      module = context.support.authModule
      loggedOutAt <- IO.realTimeInstant
      _ <- IO.blocking {
        module.transactionManager.inTransaction {
          logout(context, token, loggedOutAt)
        }
      }
    yield LogoutResponse("Logged out")

  private def logout(context: ApiPlanContext, token: String, loggedOutAt: Instant): Unit =
    val module = context.support.authModule
    AuthenticatedSessionTable.findByToken(context.connection, token).foreach { session =>
      if AuthenticatedSessionFunctions.canAuthenticate(session, loggedOutAt) then
        AuthenticatedSessionTable.save(context.connection, AuthenticatedSessionFunctions.revoke(session, loggedOutAt))
    }
