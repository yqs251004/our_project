package riichinexus.microservices.player.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.microservices.player.objects.apiTypes.{PlayerProfileView, PlayerResponse}
import upickle.default.*

final case class GetPlayerAPIMessage(
    playerId: String
) extends APIMessage[PlayerResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlayerResponse] =
    IO {
      context.support.playerModule.tables.findPlayer(PlayerId(playerId))
        .map(PlayerProfileView.fromDomain)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
