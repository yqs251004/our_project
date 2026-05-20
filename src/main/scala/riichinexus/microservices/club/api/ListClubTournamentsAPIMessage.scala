package riichinexus.microservices.club.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.ClubTournamentResponses.given
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
    IO {
      val parsedScope = scope.filter(_.nonEmpty).getOrElse("recent")
      val parsedViewer = viewer.filter(_.nonEmpty).map(PlayerId(_))
      val parsedClubId = ClubId(clubId)
      context.support.clubModule.tables
        .findClub(parsedClubId)
        .getOrElse(throw NoSuchElementException(s"Club ${parsedClubId.value} was not found"))
      val viewerPrincipal = parsedViewer.map(context.support.principal).getOrElse(AccessPrincipal.guest())
      val allItems = context.support.clubModule.tables
        .listTournamentsByClub(parsedClubId)
        .flatMap(tournament => buildClubTournamentParticipationView(context, parsedClubId, tournament, viewerPrincipal))
      val recentThreshold = Instant.now().minus(recentTournamentWindow)
      val items = parsedScope.trim.toLowerCase match
        case "recent" =>
          allItems.filter(item =>
            item.status == TournamentStatus.RegistrationOpen ||
              item.status == TournamentStatus.Scheduled ||
              item.status == TournamentStatus.InProgress ||
              item.endsAt.isAfter(recentThreshold)
          )
        case "active" =>
          allItems.filter(item =>
            item.status == TournamentStatus.RegistrationOpen ||
              item.status == TournamentStatus.Scheduled ||
              item.status == TournamentStatus.InProgress
          )
        case "all" => allItems
        case other =>
          throw IllegalArgumentException(
            s"Unsupported scope '$other'. Supported values: recent, active, all"
          )
      val sortedItems = items.sortBy(item => (item.startsAt, item.tournamentId.value)).reverse
      val resolvedLimit = limit.getOrElse(20)
      val resolvedOffset = offset.getOrElse(0)
      require(resolvedLimit > 0, "Input field limit must be positive")
      require(resolvedOffset >= 0, "Input field offset must be non-negative")
      val boundedLimit = math.min(resolvedLimit, 100)
      val page = sortedItems.slice(resolvedOffset, resolvedOffset + boundedLimit)
      PagedResponse(
        items = page,
        total = sortedItems.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < sortedItems.size,
        appliedFilters = Vector(
          scope.filter(_.nonEmpty).map("scope" -> _),
          viewer.filter(_.nonEmpty).map("viewer" -> _)
        ).flatten.toMap
      )
    }

  private def buildClubTournamentParticipationView(
      context: ApiPlanContext,
      clubId: ClubId,
      tournament: Tournament,
      viewer: AccessPrincipal
  ): Option[ClubTournamentParticipationView] =
    val club = context.support.clubModule.tables.findClub(clubId)
    val clubVisibleToViewer =
      club.exists(currentClub => canManageClubTournamentParticipation(context, viewer, currentClub))
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
          clubId = clubId,
          tournamentId = tournament.id,
          name = tournament.name,
          status = tournament.status,
          clubParticipationStatus =
            if isParticipating then ClubTournamentParticipationStatus.Participating
            else ClubTournamentParticipationStatus.Invited,
          stageName = stageName,
          startsAt = tournament.startsAt,
          endsAt = tournament.endsAt,
          canViewDetail = tournament.status != TournamentStatus.Draft || clubVisibleToViewer,
          canSubmitLineup =
            clubVisibleToViewer &&
              tournament.status != TournamentStatus.Draft &&
              tournament.status != TournamentStatus.Cancelled &&
              tournament.status != TournamentStatus.Archived &&
              (isWhitelisted || isParticipating),
          canDecline =
            clubVisibleToViewer &&
              tournament.status != TournamentStatus.Completed &&
              tournament.status != TournamentStatus.Cancelled &&
              tournament.status != TournamentStatus.Archived
        )
      )

  private def canManageClubTournamentParticipation(
      context: ApiPlanContext,
      actor: AccessPrincipal,
      club: Club
  ): Boolean =
    actor.isSuperAdmin ||
      context.support.clubModule.authorizationService.can(actor, Permission.SubmitTournamentLineup, clubId = Some(club.id)) ||
      actor.playerId.exists(playerId =>
        club.members.contains(playerId) && club.hasPrivilege(playerId, ClubPrivilege.PriorityLineup)
      )
