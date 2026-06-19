package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.{ResolveAccessPrincipalPrivateAPIMessage, ResolveAnonymousGuestAccessPrincipalPrivateAPIMessage}

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.objects.`private`.competition.TournamentPrivateView

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.tournamentparticipation.{ClubTournamentParticipationStatus, ClubTournamentScope}

import riichinexus.microservices.club.objects.tournamentparticipation.apiTypes.ClubTournamentParticipationView
import riichinexus.microservices.tournament.objects.stage.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.tournament.api.`private`.ListClubTournamentsPrivateAPIMessage
import riichinexus.system.objects.PagedResponse
/** 列出俱乐部收到或参与的赛事。 */
final case class ListClubTournamentsAPIMessage(
    clubId: String,
    scope: Option[ClubTournamentScope] = None,
    viewer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[ClubTournamentParticipationView]]:
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
    parsedViewer.map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context)).getOrElse(ResolveAnonymousGuestAccessPrincipalPrivateAPIMessage().plan(context)).map { viewerPrincipal =>
      ClubTournamentQuery(
        clubId = ClubId(clubId),
        scope = scope.getOrElse(ClubTournamentScope.Recent),
        viewerPrincipal = viewerPrincipal,
        limit = limit.getOrElse(20),
        offset = offset.getOrElse(0),
        recentThreshold = now.minus(recentTournamentWindow),
        appliedFilters = Vector(
          scope.map(value => "scope" -> ClubTournamentScope.toString(value)),
          viewer.filter(_.nonEmpty).map("viewer" -> _)
        ).flatten.toMap
      )
    }

  private def listTournaments(
      connection: java.sql.Connection,
      query: ClubTournamentQuery,
      tournaments: Vector[TournamentPrivateView]
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
    query.scope match
      case ClubTournamentScope.Recent =>
        items.filter(item =>
          activeStatuses.contains(item.status) ||
            Instant.parse(item.endsAt).isAfter(query.recentThreshold)
        )
      case ClubTournamentScope.Active =>
        items.filter(item => activeStatuses.contains(item.status))
      case ClubTournamentScope.All => items

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
      tournament: TournamentPrivateView,
      viewer: AccessPrincipalPrivateView
  ): Option[ClubTournamentParticipationView] =
    val club = ClubTable.findById(connection, clubId)
    val clubVisibleToViewer =
      club.exists(currentClub => ClubAuthorization.canManageClubTournamentParticipation(viewer, currentClub))
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
          status = tournament.status,
          clubParticipationStatus =
            if isParticipating then ClubTournamentParticipationStatus.Participating
            else ClubTournamentParticipationStatus.Invited,
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
    TournamentStatus.RegistrationOpen,
    TournamentStatus.Scheduled,
    TournamentStatus.InProgress
  )

  private final case class ClubTournamentQuery(
      clubId: ClubId,
      scope: ClubTournamentScope,
      viewerPrincipal: AccessPrincipalPrivateView,
      limit: Int,
      offset: Int,
      recentThreshold: Instant,
      appliedFilters: Map[String, String]
  )
