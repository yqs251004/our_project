package riichinexus.microservices.auth.api.session


import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIWithTokenMessage, ApiPlanContext}
import riichinexus.microservices.auth.domain.session.functions.AuthenticatedSessionFunctions
import riichinexus.microservices.auth.objects.session.apiTypes.LogoutResponse
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
/** 注销当前 token 对应的登录会话。 */
final case class LogoutAuthAPIMessage() extends APIWithTokenMessage[LogoutResponse]:

  override def plan(context: ApiPlanContext): IO[LogoutResponse] =
    for
      token <- IO.blocking(ApiPlanContext.requireBearerToken(context))
      loggedOutAt <- IO.realTimeInstant
      _ <- IO.blocking {
        {
          logout(context, token, loggedOutAt)
        }
      }
    yield LogoutResponse("Logged out")

  private def logout(context: ApiPlanContext, token: String, loggedOutAt: Instant): Unit =
    AuthenticatedSessionTable.findByToken(context.connection, token).foreach { session =>
      if AuthenticatedSessionFunctions.canAuthenticate(session, loggedOutAt) then
        AuthenticatedSessionTable.save(context.connection, AuthenticatedSessionFunctions.revoke(session, loggedOutAt))
    }
