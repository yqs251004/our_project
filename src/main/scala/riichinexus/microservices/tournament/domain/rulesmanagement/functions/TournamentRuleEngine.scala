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
import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.StageAdvancementSnapshot
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.StageRankingSnapshot

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
