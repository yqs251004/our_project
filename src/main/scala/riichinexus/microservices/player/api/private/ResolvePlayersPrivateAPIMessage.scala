package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.player.tables.players.PlayerTable
import upickle.default.*

final case class ResolvePlayersPrivateAPIMessage(
    playerIds: Vector[PlayerId]
) extends APIMessage[Vector[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Player]] =
    IO.blocking(PlayerTable.findByIds(context.connection, playerIds))
