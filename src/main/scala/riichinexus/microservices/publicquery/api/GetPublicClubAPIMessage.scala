package riichinexus.microservices.publicquery.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.ClubId
import riichinexus.microservices.publicquery.objects.apiTypes.PublicClubDetailView
import riichinexus.microservices.publicquery.tables.PublicClubDetailQueries
import upickle.default.*

final case class GetPublicClubAPIMessage(
    clubId: String
) extends APIMessage[PublicClubDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PublicClubDetailView] =
    for
      id <- IO(ClubId(clubId))
      club <- IO(findPublicClub(context, id))
    yield club

  private def findPublicClub(context: ApiPlanContext, clubId: ClubId): PublicClubDetailView =
    PublicClubDetailQueries
      .detail(context.support.clubModule, clubId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
