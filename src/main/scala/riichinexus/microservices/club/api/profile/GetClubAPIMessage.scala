package riichinexus.microservices.club.api.profile
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.club.domain.profile.model.Club

import riichinexus.microservices.club.objects.profile.ClubView
import riichinexus.microservices.club.tables.clubs.ClubTable
/** 获取管理视角的俱乐部详情。 */
final case class GetClubAPIMessage(clubId: String) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      id <- IO.pure(ClubId(clubId))
      club <- IO.blocking(resolveClub(context, id))
    yield ClubViewFunctions.clubView(club)

  private def resolveClub(context: ApiPlanContext, clubId: ClubId): Club =
    ClubTable.findById(context.connection, clubId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
