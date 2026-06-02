package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.tables.players.PlayerTable
import upickle.default.*

final case class ResolvePlayerByUserIdPrivateAPIMessage(
    userId: String
) extends APIMessage[Option[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Player]] =
    IO.blocking(PlayerTable.findByUserId(context.connection, userId))
