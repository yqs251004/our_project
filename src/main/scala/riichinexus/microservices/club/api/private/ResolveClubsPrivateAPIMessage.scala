package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.ClubId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.*

final case class ResolveClubsPrivateAPIMessage(
    clubIds: Vector[ClubId]
) extends APIMessage[Vector[Club]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Club]] =
    for
      clubs <- IO.blocking(ClubTable.findByIds(context.connection, clubIds))
    yield clubs
