package riichinexus.microservices.player.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.microservices.player.objects.apiTypes.{PlayerProfileView, PlayerResponse}
import upickle.default.*

final case class GetCurrentPlayerAPIMessage(
    operatorId: String
) extends APIMessage[PlayerResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlayerResponse] =
    for
      playerId <- IO(resolvePlayerId)
      player <- IO(
        context.support.playerModule.tables.findPlayer(playerId)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      )
    yield PlayerProfileView.fromDomain(player)

  private def resolvePlayerId: PlayerId =
    Option(operatorId).map(_.trim).filter(_.nonEmpty)
      .map(PlayerId(_))
      .getOrElse(throw IllegalArgumentException("Input field operatorId is required"))
