package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.domain.Club

import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.ReadWriter

/** 获取管理视角的俱乐部详情。 */
final case class GetClubAPIMessage(clubId: String) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      id <- IO.pure(ClubId(clubId))
      club <- IO.blocking(resolveClub(context, id))
    yield ClubView.fromDomain(club)

  private def resolveClub(context: ApiPlanContext, clubId: ClubId): Club =
    ClubTable.findById(context.connection, clubId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
