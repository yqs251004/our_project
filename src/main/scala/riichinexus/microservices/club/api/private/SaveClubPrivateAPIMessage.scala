package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.*

final case class SaveClubPrivateAPIMessage(
    club: Club
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    for
      saved <- IO.blocking(ClubTable.save(context.connection, club))
    yield saved
