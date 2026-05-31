package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.ClubId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.model.Club
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.*

final case class ResolveClubPrivateAPIMessage(
    clubId: ClubId
) extends APIMessage[Option[Club]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Club]] =
    for
      club <- IO.blocking(ClubTable.findById(context.connection, clubId))
    yield club
