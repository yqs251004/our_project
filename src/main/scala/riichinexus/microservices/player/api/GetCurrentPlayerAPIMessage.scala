package riichinexus.microservices.player.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.microservices.player.objects.PlayerProfileView
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class GetCurrentPlayerAPIMessage(
    operatorId: String
) extends APIMessage[PlayerProfileView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlayerProfileView] =
    for
      playerId <- IO(resolvePlayerId)
      player <- IO(findPlayer(context.connection, playerId))
    yield PlayerProfileView.fromDomain(player)

  private def resolvePlayerId: PlayerId =
    Option(operatorId).map(_.trim).filter(_.nonEmpty)
      .map(PlayerId(_))
      .getOrElse(throw IllegalArgumentException("Input field operatorId is required"))

  private def findPlayer(connection: java.sql.Connection, playerId: PlayerId) =
    PlayerTable.findById(connection, playerId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
