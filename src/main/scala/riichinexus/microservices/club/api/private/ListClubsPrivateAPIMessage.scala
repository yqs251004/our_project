package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{ClubId, PlayerId}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.*

final case class ListClubsPrivateAPIMessage(
    activeOnly: Boolean = false,
    joinableOnly: Boolean = false,
    memberId: Option[PlayerId] = None,
    adminId: Option[PlayerId] = None,
    name: Option[String] = None
) extends APIMessage[Vector[Club]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Club]] =
    for
      clubs <- IO.blocking {
        ClubTable.findFiltered(
          context.connection,
          activeOnly = activeOnly,
          joinableOnly = joinableOnly,
          memberId = memberId,
          adminId = adminId,
          name = name
        )
      }
    yield clubs
