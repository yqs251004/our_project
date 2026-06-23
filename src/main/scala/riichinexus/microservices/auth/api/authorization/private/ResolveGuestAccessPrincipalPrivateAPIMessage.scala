package riichinexus.microservices.auth.api.authorization.`private`

import cats.effect.IO
import riichinexus.microservices.auth.domain.authorization.functions.AccessPrincipalFunctions
import riichinexus.microservices.auth.domain.authorization.functions.AccessPrincipalPrivateViewFunctions
import riichinexus.microservices.auth.api.session.`private`.ResolveGuestSessionAuthPrivateAPIMessage
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.session.GuestSessionId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端服务解析游客会话访问主体。 */
final case class ResolveGuestAccessPrincipalPrivateAPIMessage(
    sessionId: GuestSessionId
) extends APIMessage[AccessPrincipalPrivateView]:

  override def plan(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    ResolveGuestSessionAuthPrivateAPIMessage(sessionId).plan(context)
      .map(session => AccessPrincipalPrivateViewFunctions.toPrivateView(AccessPrincipalFunctions.guest(session)))
