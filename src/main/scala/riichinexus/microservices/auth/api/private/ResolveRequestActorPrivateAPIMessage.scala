package riichinexus.microservices.auth.api.`private`

import cats.effect.IO
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供后端服务从请求上下文解析访问主体。 */
final case class ResolveRequestActorPrivateAPIMessage(
    guestSessionId: Option[GuestSessionId],
    operatorId: Option[PlayerId]
) extends APIMessage[AccessPrincipalPrivateView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    if guestSessionId.nonEmpty && operatorId.nonEmpty then
      IO.raiseError(IllegalArgumentException("guestSessionId and operatorId cannot be provided together"))
    else
      guestSessionId match
        case Some(sessionId) => ResolveGuestAccessPrincipalPrivateAPIMessage(sessionId).plan(context)
        case None =>
          operatorId match
            case Some(playerId) => ResolveAccessPrincipalPrivateAPIMessage(playerId).plan(context)
            case None           => ResolveAnonymousGuestAccessPrincipalPrivateAPIMessage().plan(context)
