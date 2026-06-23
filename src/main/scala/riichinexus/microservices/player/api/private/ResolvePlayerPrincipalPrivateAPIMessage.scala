package riichinexus.microservices.player.api.`private`

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.player.domain.functions.PlayerPrincipalFunctions
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供认证服务解析玩家访问主体素材。 */
final case class ResolvePlayerPrincipalPrivateAPIMessage(
    playerId: PlayerId
) extends APIMessage[AccessPrincipalPrivateView]:

  override def plan(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    IO.blocking(PlayerTable.findById(context.connection, playerId))
      .map(
        _.map(PlayerPrincipalFunctions.asPrincipal)
          .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
      )
