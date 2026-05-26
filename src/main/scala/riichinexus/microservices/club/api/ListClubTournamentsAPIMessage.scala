package riichinexus.microservices.club.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.{ClubTournamentParticipationStatus, ClubTournamentParticipationView}
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.tournament.tables.tournament.TournamentTable
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
      module = context.support.clubModule
      query <- IO(resolveQuery(context, now))
      items <- IO(listTournaments(context.connection, module, query))
    yield pagedResponse(items, query)

  private def resolveQuery(context: ApiPlanContext, now: Instant): ClubTournamentQuery =
    val parsedViewer = viewer.filter(_.nonEmpty).map(PlayerId(_))
    ClubTournamentQuery(
      clubId = ClubId(clubId),
      scope = scope.filter(_.nonEmpty).getOrElse("recent"),
      viewerPrincipal = parsedViewer.map(context.principal).getOrElse(AccessPrincipal.guest()),
      limit = limit.getOrElse(20),
      offset = offset.getOrElse(0),
      recentThreshold = now.minus(recentTournamentWindow),
      appliedFilters = Vector(
        scope.filter(_.nonEmpty).map("scope" -> _),
        viewer.filter(_.nonEmpty).map("viewer" -> _)
      ).flatten.toMap
    )

  private def listTournaments(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      query: ClubTournamentQuery
  ): Vector[ClubTournamentParticipationView] =
    ClubTable
      .findById(connection, query.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${query.clubId.value} was not found"))
    val allItems = TournamentTable
      .findByClub(connection, query.clubId)
      .flatMap(tournament => buildClubTournamentParticipationView(connection, module, query.clubId, tournament, query.viewerPrincipal))
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
      module: ClubModuleContext,
      clubId: ClubId,
      tournament: Tournament,
      viewer: AccessPrincipal
  ): Option[ClubTournamentParticipationView] =
    val club = ClubTable.findById(connection, clubId)
    val clubVisibleToViewer =
      club.exists(currentClub => ClubAuthorization.canManageClubTournamentParticipation(module, viewer, currentClub))
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
            if isParticipating then ClubTournamentParticipationStatus.Participating.toString
            else ClubTournamentParticipationStatus.Invited.toString,
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
