package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.player.api.`private`.ResolvePlayerReadModelsPrivateAPIMessage

import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.club.api.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.`private`.ClubPrivateView
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.objects.stage.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.microservices.tournament.objects.stage.table.TableStatus
import riichinexus.microservices.tournament.objects.competition.apiTypes.PublicScheduleView
import riichinexus.microservices.tournament.domain.stage.functions.lineup.StageLineupResolver
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.objects.PagedResponse
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 列出前端公开赛程。 */
final case class ListPublicSchedulesAPIMessage(
    tournamentStatus: Option[String] = None,
    stageStatus: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicScheduleView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicScheduleView]] =
    for
      _ <- requirePublicSchedulePermission(context)
      query <- IO.blocking(resolveQuery(context))
      tournaments <- IO.blocking(publicTournaments(context))
      lineupPlayersById <- lineupPlayersById(context, tournaments)
      tablesByStage <- IO.blocking(tablesByStageKey(context, tournaments))
      clubsById <- participatingClubsById(context, tournaments)
      schedules <- IO.blocking(publicScheduleViews(tournaments, lineupPlayersById, tablesByStage, clubsById))
      filteredSchedules <- IO.blocking(filterPublicScheduleViews(schedules, query))
    yield PagedResponse.fromItems(filteredSchedules, limit, offset, query.appliedFilters)(identity)

  private def requirePublicSchedulePermission(context: ApiPlanContext): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      permission = Permission.ViewPublicSchedule
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure("guest is not allowed to view public schedule"))
    }

  private def resolveQuery(context: ApiPlanContext): ResolvedScheduleQuery =
    ResolvedScheduleQuery(
      tournamentStatus = tournamentStatus.filter(_.nonEmpty).map(
        riichinexus.system.EnumParsing.parse("tournamentStatus", _)(TournamentStatus.valueOf)
      ),
      stageStatus = stageStatus.filter(_.nonEmpty).map(
        riichinexus.system.EnumParsing.parse("stageStatus", _)(StageStatus.valueOf)
      ),
      appliedFilters = Vector(
        tournamentStatus.filter(_.nonEmpty).map("tournamentStatus" -> _),
        stageStatus.filter(_.nonEmpty).map("stageStatus" -> _)
      ).flatten.toMap
    )

  private def publicTournaments(context: ApiPlanContext): Vector[Tournament] =
    TournamentTable.findFiltered(context.connection, includeDraft = false)

  private def lineupPlayersById(
      context: ApiPlanContext,
      tournaments: Vector[Tournament]
  ): IO[Map[PlayerId, PlayerPrivateView]] =
    ResolvePlayerReadModelsPrivateAPIMessage(
      tournaments.flatMap(_.stages.flatMap(_.lineupSubmissions.flatMap(_.seats.map(_.playerId)))).distinct
    ).plan(context)
      .map(_.map(player => player.id -> player).toMap)

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
  ): IO[Map[ClubId, ClubPrivateView]] =
    ResolveClubReadModelsPrivateAPIMessage(tournaments.flatMap(_.participatingClubs).distinct)
      .plan(context)
      .map(_.map(club => club.id -> club).toMap)

  private def publicScheduleViews(
      tournaments: Vector[Tournament],
      lineupPlayersById: Map[PlayerId, PlayerPrivateView],
      tablesByStage: Map[(TournamentId, TournamentStageId), Vector[Table]],
      clubsById: Map[ClubId, ClubPrivateView]
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
      .filter(schedule => query.tournamentStatus.forall(_ == schedule.tournamentStatus))
      .filter(schedule => query.stageStatus.forall(_ == schedule.stageStatus))
      .sortBy(schedule => (schedule.startsAt, schedule.tournamentName, schedule.stageName))

  private final case class ResolvedScheduleQuery(
      tournamentStatus: Option[TournamentStatus],
      stageStatus: Option[StageStatus],
      appliedFilters: Map[String, String]
  )
