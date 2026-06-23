package riichinexus.microservices.club.api.participation
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAnonymousGuestAccessPrincipalPrivateAPIMessage

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.objects.competition.`private`.TournamentPrivateView

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.participation.{ClubTournamentParticipationStatus, ClubTournamentScope}

import riichinexus.microservices.club.objects.participation.ClubTournamentParticipationView
import riichinexus.microservices.tournament.objects.stage.lifecycle.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.tournament.api.competition.`private`.ListClubTournamentsPrivateAPIMessage
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
      requestedClubId = ClubId(clubId)
      resolvedScope = scope.getOrElse(ClubTournamentScope.Recent)
      resolvedLimit = limit.getOrElse(20)
      resolvedOffset = offset.getOrElse(0)
      recentThreshold = now.minus(recentTournamentWindow)
      appliedFilters = clubTournamentFilters
      viewerPrincipal <- resolveViewerPrincipal(context)
      tournaments <- ListClubTournamentsPrivateAPIMessage(requestedClubId).plan(context)
      items <- IO.blocking(listTournaments(context.connection, requestedClubId, resolvedScope, viewerPrincipal, recentThreshold, tournaments))
    yield pagedResponse(items, resolvedLimit, resolvedOffset, appliedFilters)

  private def resolveViewerPrincipal(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    val viewerPlayerId = viewer.filter(_.nonEmpty).map(PlayerId(_))
    viewerPlayerId.map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context)).getOrElse(ResolveAnonymousGuestAccessPrincipalPrivateAPIMessage().plan(context))

  private def clubTournamentFilters: Map[String, String] =
    Vector(
      scope.map(value => "scope" -> ClubTournamentScope.toString(value)),
      viewer.filter(_.nonEmpty).map("viewer" -> _)
    ).flatten.toMap

  private def listTournaments(
      connection: java.sql.Connection,
      clubId: ClubId,
      scope: ClubTournamentScope,
      viewerPrincipal: AccessPrincipalPrivateView,
      recentThreshold: Instant,
      tournaments: Vector[TournamentPrivateView]
  ): Vector[ClubTournamentParticipationView] =
    ClubTable
      .findById(connection, clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    val allItems = tournaments
      .flatMap(tournament => buildClubTournamentParticipationView(connection, clubId, tournament, viewerPrincipal))
    filterByScope(allItems, scope, recentThreshold)

  private def filterByScope(
      items: Vector[ClubTournamentParticipationView],
      scope: ClubTournamentScope,
      recentThreshold: Instant
  ): Vector[ClubTournamentParticipationView] =
    scope match
      case ClubTournamentScope.Recent =>
        items.filter(item =>
          activeStatuses.contains(item.status) ||
            Instant.parse(item.endsAt).isAfter(recentThreshold)
        )
      case ClubTournamentScope.Active =>
        items.filter(item => activeStatuses.contains(item.status))
      case ClubTournamentScope.All => items

  private def pagedResponse(
      items: Vector[ClubTournamentParticipationView],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  ): PagedResponse[ClubTournamentParticipationView] =
    val sortedItems = items.sortBy(item => (item.startsAt, item.tournamentId)).reverse
    require(limit > 0, "Input field limit must be positive")
    require(offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(limit, 100)
    val page = sortedItems.slice(offset, offset + boundedLimit)
    PagedResponse(
      items = page,
      total = sortedItems.size,
      limit = boundedLimit,
      offset = offset,
      hasMore = offset + page.size < sortedItems.size,
      appliedFilters = appliedFilters
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
