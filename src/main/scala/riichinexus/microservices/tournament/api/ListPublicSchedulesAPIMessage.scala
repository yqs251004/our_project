package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import cats.effect.unsafe.implicits.global
import riichinexus.system.api.ApiPlanContext
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
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.microservices.club.api.`private`.ResolveClubsPrivateAPIMessage
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentStatus}
import riichinexus.microservices.tournament.objects.tablemanagement.TableStatus
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.PublicScheduleView
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.StageLineupResolver
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.objects.PagedResponse
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

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
      lineupPlayersById <- IO.blocking(lineupPlayersById(context, tournaments))
      tablesByStage <- IO.blocking(tablesByStageKey(context, tournaments))
      clubsById <- IO.blocking(participatingClubsById(context, tournaments))
      schedules <- IO.blocking(publicScheduleViews(tournaments, lineupPlayersById, tablesByStage, clubsById))
      filteredSchedules <- IO.blocking(filterPublicScheduleViews(schedules, query))
    yield PagedResponse.fromItems(filteredSchedules, limit, offset, query.appliedFilters)(identity)

  private def requirePublicSchedulePermission(context: ApiPlanContext): IO[Unit] =
    val guest = AccessPrincipalFunctions.guest()
    AuthCheckPermissionAPIMessage(
      principal = Some(guest),
      permission = Permission.ViewPublicSchedule
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${guest.displayName} is not allowed to view public schedule"))
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
  ): Map[PlayerId, Player] =
    PlayerPersistenceFunctions.findPlayersByIds(context.connection, tournaments.flatMap(_.stages.flatMap(_.lineupSubmissions.flatMap(_.seats.map(_.playerId)))).distinct)
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
    ResolveClubsPrivateAPIMessage(tournaments.flatMap(_.participatingClubs).distinct)
      .plan(ApiPlanContext(bearerToken = None, connection = context.connection))
      .unsafeRunSync()
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
      .filter(schedule => query.tournamentStatus.forall(_ == schedule.tournamentStatus))
      .filter(schedule => query.stageStatus.forall(_ == schedule.stageStatus))
      .sortBy(schedule => (schedule.startsAt, schedule.tournamentName, schedule.stageName))

  private final case class ResolvedScheduleQuery(
      tournamentStatus: Option[TournamentStatus],
      stageStatus: Option[StageStatus],
      appliedFilters: Map[String, String]
  )
