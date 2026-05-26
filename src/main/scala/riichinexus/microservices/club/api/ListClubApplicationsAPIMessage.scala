package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubApplicationViewAssembler
import riichinexus.microservices.club.objects.ClubApplicationStatus
import riichinexus.microservices.club.objects.ClubMembershipApplicationView
import riichinexus.microservices.club.objects.apiTypes.ClubApplicationListQuery
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListClubApplicationsAPIMessage(
    clubId: String,
    query: ClubApplicationListQuery
) extends APIMessage[PagedResponse[ClubMembershipApplicationView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubMembershipApplicationView]] =
    for
      resolved <- IO(resolveQuery(context))
      page <- IO(listApplications(context, resolved))
    yield page

  private def resolveQuery(context: ApiPlanContext): ResolvedClubApplicationListQuery =
    ResolvedClubApplicationListQuery(
      clubId = ClubId(clubId),
      operatorId = Option(query.operatorId).filter(_.nonEmpty).map(PlayerId(_)),
      status = query.status,
      applicantUserId = query.applicantUserId.filter(_.nonEmpty),
      displayName = query.displayName.filter(_.nonEmpty),
      limit = query.limit.getOrElse(20),
      offset = query.offset.getOrElse(0),
      appliedFilters = Vector(
        Option(query.operatorId).filter(_.nonEmpty).map("operatorId" -> _),
        query.status.map(status => "status" -> status.toString),
        query.applicantUserId.filter(_.nonEmpty).map("applicantUserId" -> _),
        query.displayName.filter(_.nonEmpty).map("displayName" -> _)
      ).flatten.toMap
    )

  private def listApplications(
      context: ApiPlanContext,
      query: ResolvedClubApplicationListQuery
  ): PagedResponse[ClubMembershipApplicationView] =
    val module = context.support.clubModule
    val club = ClubTable
      .findById(context.connection, query.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${query.clubId.value} was not found"))
    val actor = context.requestActor(
      guestSessionId = None,
      operatorId = query.operatorId
    )
    requireClubApplicationManager(actor, club)

    val applications = club.membershipApplications
      .filter(application => query.status.forall(_ == application.status))
      .filter(application => query.applicantUserId.forall(value => application.applicantUserId.contains(value)))
      .filter(application => query.displayName.forall(context.support.containsIgnoreCase(application.displayName, _)))
      .sortBy(_.submittedAt)
      .map(application => ClubApplicationViewAssembler.applicationView(context.connection, module, club, application, actor))
    pagedResponse(applications, query)

  private def pagedResponse(
      applications: Vector[ClubMembershipApplicationView],
      query: ResolvedClubApplicationListQuery
  ): PagedResponse[ClubMembershipApplicationView] =
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val page = applications.slice(query.offset, query.offset + boundedLimit)
    PagedResponse(
      items = page,
      total = applications.size,
      limit = boundedLimit,
      offset = query.offset,
      hasMore = query.offset + page.size < applications.size,
      appliedFilters = query.appliedFilters
    )

  private def requireClubApplicationManager(actor: AccessPrincipal, club: Club): Unit =
    if !ClubApplicationViewAssembler.canManageClubApplications(actor, club) then
      throw AuthorizationFailure(s"${actor.displayName} cannot manage membership applications for club ${club.id.value}")

  private final case class ResolvedClubApplicationListQuery(
      clubId: ClubId,
      operatorId: Option[PlayerId],
      status: Option[ClubApplicationStatus],
      applicantUserId: Option[String],
      displayName: Option[String],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
