package riichinexus.microservices.player.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class ListPlayersByClubPrivateAPIMessage(
    clubId: ClubId
) extends APIMessage[Vector[Player]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Player]] =
    IO.blocking(PlayerTable.findByClub(context.connection, clubId))
