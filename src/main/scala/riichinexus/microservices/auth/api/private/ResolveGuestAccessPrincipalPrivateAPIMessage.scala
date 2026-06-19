package riichinexus.microservices.auth.api.`private`

import cats.effect.IO
import riichinexus.microservices.auth.domain.functions.AccessPrincipalFunctions
import riichinexus.microservices.auth.domain.functions.AccessPrincipalPrivateViewFunctions
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端服务解析游客会话访问主体。 */
final case class ResolveGuestAccessPrincipalPrivateAPIMessage(
    sessionId: GuestSessionId
) extends APIMessage[AccessPrincipalPrivateView]:

  override def plan(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    ResolveGuestSessionAuthPrivateAPIMessage(sessionId).plan(context)
      .map(session => AccessPrincipalPrivateViewFunctions.toPrivateView(AccessPrincipalFunctions.guest(session)))
