package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class ListAllPlayersPrivateAPIMessage() extends APIMessage[Vector[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Player]] =
    IO.blocking(PlayerTable.findAll(context.connection))
