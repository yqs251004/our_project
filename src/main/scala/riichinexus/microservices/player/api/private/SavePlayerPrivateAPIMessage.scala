package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.tables.players.PlayerTable
import upickle.default.*

final case class SavePlayerPrivateAPIMessage(
    player: Player
) extends APIMessage[Player] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Player] =
    IO.blocking(PlayerTable.save(context.connection, player))
