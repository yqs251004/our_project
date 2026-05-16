package riichinexus.domain.service

import java.time.Instant

import riichinexus.domain.model.*

trait TournamentRuleEngine:
  def buildStageRanking(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerId],
      records: Vector[MatchRecord],
      at: Instant = Instant.now()
  ): StageRankingSnapshot

  def projectAdvancement(
      tournament: Tournament,
      stage: TournamentStage,
      ranking: StageRankingSnapshot,
      at: Instant = Instant.now()
  ): StageAdvancementSnapshot

  def buildKnockoutBracket(
      tournament: Tournament,
      stage: TournamentStage,
      advancement: StageAdvancementSnapshot,
      participants: Vector[Player],
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot

  def buildKnockoutProgression(
      tournament: Tournament,
      stage: TournamentStage,
      advancement: StageAdvancementSnapshot,
      participants: Vector[Player],
      tables: Vector[Table],
      records: Vector[MatchRecord],
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot

final class DefaultTournamentRuleEngine extends TournamentRuleEngine:
  override def buildStageRanking(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerId],
      records: Vector[MatchRecord],
      at: Instant
  ): StageRankingSnapshot =
    TournamentStageRankingBuilder.build(tournament, stage, participants, records, at)

  override def projectAdvancement(
      tournament: Tournament,
      stage: TournamentStage,
      ranking: StageRankingSnapshot,
      at: Instant
  ): StageAdvancementSnapshot =
    TournamentAdvancementProjector.project(tournament, stage, ranking, at)

  override def buildKnockoutBracket(
      tournament: Tournament,
      stage: TournamentStage,
      advancement: StageAdvancementSnapshot,
      participants: Vector[Player],
      at: Instant
  ): KnockoutBracketSnapshot =
    buildKnockoutProgression(tournament, stage, advancement, participants, Vector.empty, Vector.empty, at)

  override def buildKnockoutProgression(
      tournament: Tournament,
      stage: TournamentStage,
      advancement: StageAdvancementSnapshot,
      participants: Vector[Player],
      tables: Vector[Table],
      records: Vector[MatchRecord],
      at: Instant
  ): KnockoutBracketSnapshot =
    TournamentKnockoutBracketBuilder.build(tournament, stage, advancement, participants, tables, records, at)
