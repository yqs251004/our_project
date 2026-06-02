package riichinexus.microservices.club.api
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.api.`private`.ClubApplicationViewAssembler
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubMembershipApplicationView
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubApplicationListQuery
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListClubApplicationsAPIMessage(
    clubId: String,
    query: ClubApplicationListQuery
) extends APIMessage[PagedResponse[ClubMembershipApplicationView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubMembershipApplicationView]] =
    for
      resolved <- IO.blocking(resolveQuery(context))
      actor <- ResolveRequestActor(guestSessionId = None, operatorId = resolved.operatorId).plan(context)
      page <- IO.blocking(listApplications(context, resolved, actor))
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
        query.status.map(status => "status" -> ClubApplicationStatus.toString(status)),
        query.applicantUserId.filter(_.nonEmpty).map("applicantUserId" -> _),
        query.displayName.filter(_.nonEmpty).map("displayName" -> _)
      ).flatten.toMap
    )

  private def listApplications(
      context: ApiPlanContext,
      query: ResolvedClubApplicationListQuery,
      actor: AccessPrincipal
  ): PagedResponse[ClubMembershipApplicationView] =
    val club = ClubTable
      .findById(context.connection, query.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${query.clubId.value} was not found"))
    ClubAuthorization.requireClubApplicationManager(actor, club)

    val applications = club.membershipApplications
      .filter(application => query.status.forall(_ == application.status))
      .filter(application => query.applicantUserId.forall(value => application.applicantUserId.contains(value)))
      .filter(application => query.displayName.forall(riichinexus.system.TextSearch.containsIgnoreCase(application.displayName, _)))
      .sortBy(_.submittedAt)
      .map(application => ClubApplicationViewAssembler.applicationView(context.connection, club, application, actor))
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
