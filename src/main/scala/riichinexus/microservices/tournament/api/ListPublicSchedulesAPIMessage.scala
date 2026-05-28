package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.Club
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.player.objects.Player
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.tournament.objects.apiTypes.PublicScheduleView
import riichinexus.microservices.tournament.domain.StageLineupResolver
import riichinexus.microservices.tournament.tables.tournament.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListPublicSchedulesAPIMessage(
    tournamentStatus: Option[String] = None,
    stageStatus: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicScheduleView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicScheduleView]] =
    for
      query <- IO.blocking(resolveQuery(context))
      tournaments <- IO.blocking(publicTournaments(context))
      lineupPlayersById <- IO.blocking(lineupPlayersById(context, tournaments))
      tablesByStage <- IO.blocking(tablesByStageKey(context, tournaments))
      clubsById <- IO.blocking(participatingClubsById(context, tournaments))
      schedules <- IO.blocking(publicScheduleViews(tournaments, lineupPlayersById, tablesByStage, clubsById))
      filteredSchedules <- IO.blocking(filterPublicScheduleViews(schedules, query))
    yield PagedResponse.fromItems(filteredSchedules, limit, offset, query.appliedFilters)(identity)

  private def resolveQuery(context: ApiPlanContext): ResolvedScheduleQuery =
    context.support.authorizationService
      .requirePermission(AccessPrincipal.guest(), Permission.ViewPublicSchedule)
    ResolvedScheduleQuery(
      tournamentStatus = tournamentStatus.filter(_.nonEmpty).map(
        context.support.parseEnum("tournamentStatus", _)(TournamentStatus.valueOf)
      ),
      stageStatus = stageStatus.filter(_.nonEmpty).map(
        context.support.parseEnum("stageStatus", _)(StageStatus.valueOf)
      ),
      appliedFilters = Vector(
        tournamentStatus.filter(_.nonEmpty).map("tournamentStatus" -> _),
        stageStatus.filter(_.nonEmpty).map("stageStatus" -> _)
      ).flatten.toMap
    )

  private def publicTournaments(context: ApiPlanContext): Vector[Tournament] =
    TournamentTable.findPublic(context.connection)

  private def lineupPlayersById(
      context: ApiPlanContext,
      tournaments: Vector[Tournament]
  ): Map[PlayerId, Player] =
    PlayerTable
      .findByIds(context.connection, tournaments.flatMap(_.stages.flatMap(_.lineupSubmissions.flatMap(_.seats.map(_.playerId)))).distinct)
      .map(player => player.id -> player)
      .toMap

  private def tablesByStageKey(
      context: ApiPlanContext,
      tournaments: Vector[Tournament]
  ): Map[(TournamentId, TournamentStageId), Vector[Table]] =
    TournamentGameTable.findByTournamentIds(context.connection, tournaments.map(_.id))
      .groupBy(table => table.tournamentId -> table.stageId)
      .withDefaultValue(Vector.empty)

  private def participatingClubsById(
      context: ApiPlanContext,
      tournaments: Vector[Tournament]
  ): Map[ClubId, Club] =
    ClubTable.findByIds(context.connection, tournaments.flatMap(_.participatingClubs).distinct)
      .map(club => club.id -> club)
      .toMap

  private def publicScheduleViews(
      tournaments: Vector[Tournament],
      lineupPlayersById: Map[PlayerId, Player],
      tablesByStage: Map[(TournamentId, TournamentStageId), Vector[Table]],
      clubsById: Map[ClubId, Club]
  ): Vector[PublicScheduleView] =
    tournaments.flatMap { tournament =>
      tournament.stages.map { stage =>
        val stageTables = tablesByStage(tournament.id -> stage.id)
        val activeTableCount = stageTables.count(table =>
          table.status != TableStatus.Archived
        )
        val lineupPlayers = StageLineupResolver.resolveEligiblePlayers(stage, lineupPlayersById.get)
        val fallbackClubMembers = tournament.participatingClubs.flatMap { clubId =>
          clubsById.get(clubId).toVector.flatMap(_.members)
        }
        PublicScheduleView(
          tournamentId = tournament.id,
          tournamentName = tournament.name,
          tournamentStatus = tournament.status,
          stageId = stage.id,
          stageName = stage.name,
          stageStatus = stage.status,
          currentRound = stage.currentRound,
          roundCount = stage.roundCount,
          startsAt = tournament.startsAt,
          endsAt = tournament.endsAt,
          tableCount = stageTables.size,
          activeTableCount = activeTableCount,
          pendingTablePlanCount = stage.pendingTablePlans.size,
          participantCount = (lineupPlayers ++ tournament.participatingPlayers ++ fallbackClubMembers).distinct.size,
          whitelistCount = tournament.whitelist.size
        )
      }
    }

  private def filterPublicScheduleViews(
      schedules: Vector[PublicScheduleView],
      query: ResolvedScheduleQuery
  ): Vector[PublicScheduleView] =
    schedules
      .filter(schedule => query.tournamentStatus.forall(status => riichinexus.microservices.tournament.objects.TournamentStatus.fromDomain(status) == schedule.tournamentStatus))
      .filter(schedule => query.stageStatus.forall(status => riichinexus.microservices.tournament.objects.StageStatus.fromDomain(status) == schedule.stageStatus))
      .sortBy(schedule => (schedule.startsAt, schedule.tournamentName, schedule.stageName))

  private final case class ResolvedScheduleQuery(
      tournamentStatus: Option[TournamentStatus],
      stageStatus: Option[StageStatus],
      appliedFilters: Map[String, String]
  )
