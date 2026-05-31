package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.ClubView
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.*

final case class GetClubAPIMessage(clubId: String) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      id <- IO.blocking(ClubId(clubId))
      club <- IO.blocking(resolveClub(context, id))
    yield ClubView.fromDomain(club)

  private def resolveClub(context: ApiPlanContext, clubId: ClubId): Club =
    ClubTable.findById(context.connection, clubId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
