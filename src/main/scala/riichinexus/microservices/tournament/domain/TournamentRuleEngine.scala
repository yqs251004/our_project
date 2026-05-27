package riichinexus.microservices.tournament.domain

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.player.objects.Player

object TournamentRuleEngine:
  def buildStageRanking(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerId],
      records: Vector[MatchRecord],
      at: Instant = Instant.now()
  ): StageRankingSnapshot =
    TournamentStageRankingBuilder.build(tournament, stage, participants, records, at)

  def projectAdvancement(
      tournament: Tournament,
      stage: TournamentStage,
      ranking: StageRankingSnapshot,
      at: Instant = Instant.now()
  ): StageAdvancementSnapshot =
    TournamentAdvancementProjector.project(tournament, stage, ranking, at)

  def buildKnockoutBracket(
      tournament: Tournament,
      stage: TournamentStage,
      advancement: StageAdvancementSnapshot,
      participants: Vector[Player],
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    buildKnockoutProgression(tournament, stage, advancement, participants, Vector.empty, Vector.empty, at)

  def buildKnockoutProgression(
      tournament: Tournament,
      stage: TournamentStage,
      advancement: StageAdvancementSnapshot,
      participants: Vector[Player],
      tables: Vector[Table],
      records: Vector[MatchRecord],
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    TournamentKnockoutBracketBuilder.build(tournament, stage, advancement, participants, tables, records, at)
