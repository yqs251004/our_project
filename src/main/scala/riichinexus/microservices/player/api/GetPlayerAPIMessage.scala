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
    for
      id <- IO(PlayerId(playerId))
      player <- IO(findPlayer(context, id))
    yield PlayerProfileView.fromDomain(player)

  private def findPlayer(context: ApiPlanContext, playerId: PlayerId) =
    context.support.playerModule.tables
      .findPlayer(playerId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
