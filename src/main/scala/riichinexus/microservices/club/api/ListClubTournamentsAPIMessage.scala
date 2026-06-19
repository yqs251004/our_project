package riichinexus.microservices.club.api
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import riichinexus.microservices.auth.domain.authorization.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.time.{Duration, Instant}
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
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.tournamentparticipation.ClubTournamentParticipationStatus
import riichinexus.microservices.club.objects.tournamentparticipation.apiTypes.ClubTournamentParticipationView
import riichinexus.microservices.club.objects.clubmanagement.apiTypes.*
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.*
import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.*
import riichinexus.microservices.club.objects.relationmanagement.apiTypes.*
import riichinexus.microservices.club.objects.tournamentparticipation.apiTypes.*
import riichinexus.microservices.club.objects.auditreadmodel.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentStatus}
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.tournament.api.`private`.ListClubTournamentsPrivateAPIMessage
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListClubTournamentsAPIMessage(
    clubId: String,
    scope: Option[String] = None,
    viewer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[ClubTournamentParticipationView]] derives ReadWriter:
  private val recentTournamentWindow = Duration.ofDays(90)

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubTournamentParticipationView]] =
    for
      now <- IO.realTimeInstant
      query <- resolveQuery(context, now)
      tournaments <- ListClubTournamentsPrivateAPIMessage(query.clubId).plan(context)
      items <- IO.blocking(listTournaments(context.connection, query, tournaments))
    yield pagedResponse(items, query)

  private def resolveQuery(context: ApiPlanContext, now: Instant): IO[ClubTournamentQuery] =
    val parsedViewer = viewer.filter(_.nonEmpty).map(PlayerId(_))
    parsedViewer.map(ResolveAccessPrincipal(_).plan(context)).getOrElse(IO.pure(AccessPrincipalFunctions.guest())).map { viewerPrincipal =>
      ClubTournamentQuery(
        clubId = ClubId(clubId),
        scope = scope.filter(_.nonEmpty).getOrElse("recent"),
        viewerPrincipal = viewerPrincipal,
        limit = limit.getOrElse(20),
        offset = offset.getOrElse(0),
        recentThreshold = now.minus(recentTournamentWindow),
        appliedFilters = Vector(
          scope.filter(_.nonEmpty).map("scope" -> _),
          viewer.filter(_.nonEmpty).map("viewer" -> _)
        ).flatten.toMap
      )
    }

  private def listTournaments(
      connection: java.sql.Connection,
      query: ClubTournamentQuery,
      tournaments: Vector[Tournament]
  ): Vector[ClubTournamentParticipationView] =
    ClubTable
      .findById(connection, query.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${query.clubId.value} was not found"))
    val allItems = tournaments
      .flatMap(tournament => buildClubTournamentParticipationView(connection, query.clubId, tournament, query.viewerPrincipal))
    filterByScope(allItems, query)

  private def filterByScope(
      items: Vector[ClubTournamentParticipationView],
      query: ClubTournamentQuery
  ): Vector[ClubTournamentParticipationView] =
    query.scope.trim.toLowerCase match
      case "recent" =>
        items.filter(item =>
          activeStatuses.contains(item.status) ||
            Instant.parse(item.endsAt).isAfter(query.recentThreshold)
        )
      case "active" =>
        items.filter(item => activeStatuses.contains(item.status))
      case "all" => items
      case other =>
        throw IllegalArgumentException(
          s"Unsupported scope '$other'. Supported values: recent, active, all"
        )

  private def pagedResponse(
      items: Vector[ClubTournamentParticipationView],
      query: ClubTournamentQuery
  ): PagedResponse[ClubTournamentParticipationView] =
    val sortedItems = items.sortBy(item => (item.startsAt, item.tournamentId)).reverse
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val page = sortedItems.slice(query.offset, query.offset + boundedLimit)
    PagedResponse(
      items = page,
      total = sortedItems.size,
      limit = boundedLimit,
      offset = query.offset,
      hasMore = query.offset + page.size < sortedItems.size,
      appliedFilters = query.appliedFilters
    )

  private def buildClubTournamentParticipationView(
      connection: java.sql.Connection,
      clubId: ClubId,
      tournament: Tournament,
      viewer: AccessPrincipal
  ): Option[ClubTournamentParticipationView] =
    val club = ClubTable.findById(connection, clubId)
    val clubVisibleToViewer =
      club.exists(currentClub => ClubAuthorization.canManageClubTournamentParticipation(AuthorizationPolicyFunctions.strict, viewer, currentClub))
    val isWhitelisted = tournament.whitelist.exists(_.clubId.contains(clubId))
    val isParticipating = tournament.participatingClubs.contains(clubId)
    if !isWhitelisted && !isParticipating then None
    else
      val stageName = tournament.stages
        .sortBy(_.order)
        .find(stage => stage.status != StageStatus.Completed && stage.status != StageStatus.Archived)
        .orElse(tournament.stages.sortBy(_.order).lastOption)
        .map(_.name)
      Some(
        ClubTournamentParticipationView(
          clubId = clubId.value,
          tournamentId = tournament.id.value,
          name = tournament.name,
          status = tournament.status.toString,
          clubParticipationStatus =
            if isParticipating then ClubTournamentParticipationStatus.toString(ClubTournamentParticipationStatus.Participating)
            else ClubTournamentParticipationStatus.toString(ClubTournamentParticipationStatus.Invited),
          stageName = stageName,
          startsAt = tournament.startsAt.toString,
          endsAt = tournament.endsAt.toString,
          canViewDetail = tournament.status != TournamentStatus.Draft || clubVisibleToViewer,
          canSubmitLineup =
            clubVisibleToViewer &&
              tournament.status != TournamentStatus.Draft &&
              tournament.status != TournamentStatus.Cancelled &&
              tournament.status != TournamentStatus.Archived &&
              isParticipating,
          canDecline =
            clubVisibleToViewer &&
              tournament.status != TournamentStatus.Completed &&
              tournament.status != TournamentStatus.Cancelled &&
              tournament.status != TournamentStatus.Archived
        )
      )

  private val activeStatuses = Set(
    TournamentStatus.RegistrationOpen.toString,
    TournamentStatus.Scheduled.toString,
    TournamentStatus.InProgress.toString
  )

  private final case class ClubTournamentQuery(
      clubId: ClubId,
      scope: String,
      viewerPrincipal: AccessPrincipal,
      limit: Int,
      offset: Int,
      recentThreshold: Instant,
      appliedFilters: Map[String, String]
  )
