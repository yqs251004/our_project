package riichinexus.microservices.tournament.domain.stage.functions.rules


import riichinexus.microservices.tournament.domain.stage.functions.rules.knockout.KnockoutStageCoordinator
import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.model.{Table, TournamentStage}
import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.stage.rules.progression.StageAdvancementSnapshot
import riichinexus.microservices.tournament.objects.stage.ranking.StageRankingSnapshot
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

/** TournamentStageQueries 提供赛事阶段Queries 相关的领域计算、校验和转换函数。 */

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
      participants: Vector[PlayerPrivateView],
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
