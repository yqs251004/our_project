package riichinexus.microservices.auth.api.`private`

import cats.effect.IO
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrincipalPrivateAPIMessage
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端服务解析玩家访问主体。 */
final case class ResolveAccessPrincipalPrivateAPIMessage(
    playerId: PlayerId
) extends APIMessage[AccessPrincipalPrivateView]:

  override def plan(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    ResolvePlayerPrincipalPrivateAPIMessage(playerId).plan(context)
