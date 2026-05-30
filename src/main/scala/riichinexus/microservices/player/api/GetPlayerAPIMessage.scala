package riichinexus.microservices.player.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.microservices.player.objects.apiTypes.PlayerProfileView
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class GetPlayerAPIMessage(
    playerId: String
) extends APIMessage[PlayerProfileView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlayerProfileView] =
    for
      id <- IO.blocking(PlayerId(playerId))
      player <- IO.blocking(findPlayer(context.connection, id))
    yield PlayerProfileView.fromDomain(player)

  private def findPlayer(connection: java.sql.Connection, playerId: PlayerId) =
    PlayerTable.findById(connection, playerId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
