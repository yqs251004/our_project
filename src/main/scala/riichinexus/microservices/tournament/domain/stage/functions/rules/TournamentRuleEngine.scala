package riichinexus.microservices.tournament.domain.stage.functions.rules


import riichinexus.microservices.tournament.domain.stage.functions.rules.knockout.TournamentKnockoutBracketBuilder
import riichinexus.microservices.tournament.domain.stage.functions.ranking.TournamentStageRankingBuilder
import riichinexus.microservices.tournament.domain.stage.functions.rules.progression.TournamentAdvancementProjector
import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.stage.model.{Table, TournamentStage}
import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.stage.rules.progression.StageAdvancementSnapshot
import riichinexus.microservices.tournament.objects.stage.ranking.StageRankingSnapshot

/** TournamentRuleEngine 负责赛事Rule引擎 相关的领域编排、构建或投影计算。 */

private[tournament] object TournamentRuleEngine:
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
      participants: Vector[PlayerPrivateView],
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    buildKnockoutProgression(tournament, stage, advancement, participants, Vector.empty, Vector.empty, at)

  def buildKnockoutProgression(
      tournament: Tournament,
      stage: TournamentStage,
      advancement: StageAdvancementSnapshot,
      participants: Vector[PlayerPrivateView],
      tables: Vector[Table],
      records: Vector[MatchRecord],
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    TournamentKnockoutBracketBuilder.build(tournament, stage, advancement, participants, tables, records, at)
