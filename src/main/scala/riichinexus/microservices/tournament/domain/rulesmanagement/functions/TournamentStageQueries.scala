package riichinexus.microservices.tournament.domain.rulesmanagement.functions

import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
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
import riichinexus.microservices.club.api.`private`.ResolveClubsPrivateAPIMessage
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
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.StageAdvancementSnapshot
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.StageRankingSnapshot
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

private[tournament] object TournamentStageQueries:
  def stageStandings(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): IO[StageRankingSnapshot] =
    stageComputationContext(connection, tournamentId, stageId).map { context =>
      TournamentRuleEngine.buildStageRanking(
        context.tournament,
        context.stage,
        context.participants.map(_.id),
        context.records,
        at
      )
    }

  def stageAdvancementPreview(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): IO[StageAdvancementSnapshot] =
    stageComputationContext(connection, tournamentId, stageId).map { context =>
      val ranking = TournamentRuleEngine.buildStageRanking(
        context.tournament,
        context.stage,
        context.participants.map(_.id),
        context.records,
        at
      )
      TournamentRuleEngine.projectAdvancement(context.tournament, context.stage, ranking, at)
    }

  def stageKnockoutBracket(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): IO[KnockoutBracketSnapshot] =
    stageComputationContext(connection, tournamentId, stageId).map { context =>
      KnockoutStageCoordinator.buildProgression(
        tournament = context.tournament,
        stage = context.stage,
        participants = context.participants,
        records = context.records,
        tables = stageTables(connection, tournamentId, stageId),
        at = at
      )
    }

  private final case class StageComputationContext(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      records: Vector[MatchRecord]
  )

  private def stageComputationContext(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): IO[StageComputationContext] =
    for
      base <- IO.blocking {
        val tournament = TournamentTable.findById(connection, tournamentId)
          .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
        val stage = tournament.stages
          .find(_.id == stageId)
          .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))
        (tournament, stage, MatchRecordTable.findByTournamentAndStage(connection, tournamentId, stageId))
      }
      (tournament, stage, records) = base
      participants <- TournamentStageParticipantResolver.resolveParticipants(ApiPlanContext(bearerToken = None, connection = connection), tournament, stage)
    yield StageComputationContext(
      tournament = tournament,
      stage = stage,
      participants = participants,
      records = records
    )

  private def stageTables(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId)
      .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
