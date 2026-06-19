package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRatingFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class ApplyPlayerEloDeltaPrivateAPIMessage(
    playerId: PlayerId,
    delta: Int
) extends APIMessage[Option[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    PlayerPrivateMutationSupport.updatePlayer(context, playerId)(
      PlayerRatingFunctions.applyElo(_, delta)
    )
