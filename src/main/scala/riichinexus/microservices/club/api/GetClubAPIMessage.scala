package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.{Club as ClubResponse}
import upickle.default.*

final case class GetClubAPIMessage(clubId: String) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      id <- IO(ClubId(clubId))
      club <- IO(resolveClub(context, id))
    yield ClubResponse.fromDomain(club)

  private def resolveClub(context: ApiPlanContext, clubId: ClubId): Club =
    context.support.clubModule.tables.findClub(clubId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
