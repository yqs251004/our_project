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

final class DefaultTournamentRuleEngine extends TournamentRuleEngine:
  override def buildStageRanking(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerId],
      records: Vector[MatchRecord],
      at: Instant
  ): StageRankingSnapshot =
    val placementPointMap = Map(1 -> 4, 2 -> 2, 3 -> 1, 4 -> 0)

    val statsByPlayer = records
      .flatMap(_.seatResults)
      .groupBy(_.playerId)
      .view
      .mapValues { results =>
        val matchesPlayed = results.size
        val placementPoints = results.map(result => placementPointMap.getOrElse(result.placement, 0)).sum
        val totalScoreDelta = results.map(_.scoreDelta).sum
        val totalFinalPoints = results.map(_.finalPoints).sum
        val averagePlacement =
          if matchesPlayed <= 0 then 99.0
          else round2(results.map(_.placement.toDouble).sum / matchesPlayed.toDouble)

        StageStandingEntry(
          playerId = results.head.playerId,
          matchesPlayed = matchesPlayed,
          placementPoints = placementPoints,
          totalScoreDelta = totalScoreDelta,
          totalFinalPoints = totalFinalPoints,
          averagePlacement = averagePlacement
        )
      }
      .toMap

    val seededParticipants =
      participants
        .distinct
        .zipWithIndex
        .map { case (playerId, index) =>
          statsByPlayer.getOrElse(
            playerId,
            StageStandingEntry(
              playerId = playerId,
              matchesPlayed = 0,
              placementPoints = 0,
              totalScoreDelta = 0,
              totalFinalPoints = 0,
              averagePlacement = 99.0,
              seed = Some(index + 1)
            )
          )
        }

    val entries = (statsByPlayer.values.toVector ++ seededParticipants)
      .groupBy(_.playerId)
      .values
      .map(_.maxBy(entry => (entry.matchesPlayed, entry.placementPoints, entry.totalScoreDelta)))
      .toVector
      .sortBy(entry =>
        (
          -entry.placementPoints,
          -entry.totalScoreDelta,
          -entry.totalFinalPoints,
          entry.averagePlacement,
          entry.playerId.value
        )
      )
      .zipWithIndex
      .map { case (entry, index) => entry.copy(seed = Some(index + 1)) }

    StageRankingSnapshot(
      tournamentId = tournament.id,
      stageId = stage.id,
      generatedAt = at,
      entries = entries,
      archivedTableCount = records.map(_.tableId).distinct.size,
      scheduledTableCount = stage.scheduledTableIds.size
    )

  override def projectAdvancement(
      tournament: Tournament,
      stage: TournamentStage,
      ranking: StageRankingSnapshot,
      at: Instant
  ): StageAdvancementSnapshot =
    val qualifiedCount =
      stage.advancementRule.ruleType match
        case AdvancementRuleType.SwissCut =>
          stage.advancementRule.cutSize.getOrElse(defaultSwissCut(ranking.entries.size))
        case AdvancementRuleType.KnockoutElimination =>
          stage.knockoutRule.flatMap(_.bracketSize)
            .orElse(stage.advancementRule.targetTableCount.map(_ * 4))
            .getOrElse(math.min(4, ranking.entries.size))
        case AdvancementRuleType.ScoreThreshold =>
          ranking.entries.count(_.totalScoreDelta >= stage.advancementRule.thresholdScore.getOrElse(0))
        case AdvancementRuleType.Custom =>
          0

    val qualifiedEntries =
      stage.advancementRule.ruleType match
        case AdvancementRuleType.ScoreThreshold =>
          ranking.entries.filter(_.totalScoreDelta >= stage.advancementRule.thresholdScore.getOrElse(0))
        case AdvancementRuleType.Custom =>
          Vector.empty
        case _ =>
          ranking.entries.take(math.max(0, math.min(qualifiedCount, ranking.entries.size)))

    val qualifiedIds = qualifiedEntries.map(_.playerId)
    val reserveIds = ranking.entries.drop(qualifiedEntries.size).take(4).map(_.playerId)

    val decoratedStandings = ranking.entries.map { entry =>
      entry.copy(qualified = qualifiedIds.contains(entry.playerId))
    }

    StageAdvancementSnapshot(
      tournamentId = tournament.id,
      stageId = stage.id,
      generatedAt = at,
      rule = stage.advancementRule,
      standings = decoratedStandings,
      qualifiedPlayerIds = qualifiedIds,
      reservePlayerIds = reserveIds,
      summary = summaryFor(stage, qualifiedIds.size, ranking)
    )

  private def defaultSwissCut(participantCount: Int): Int =
    participantCount match
      case count if count <= 4  => count
      case count if count <= 16 => 8
      case count                => math.min(16, count)

  private def summaryFor(
      stage: TournamentStage,
      qualifiedCount: Int,
      ranking: StageRankingSnapshot
  ): String =
    stage.advancementRule.ruleType match
      case AdvancementRuleType.SwissCut =>
        s"Swiss cut selects top $qualifiedCount players from ${ranking.entries.size} ranked participants."
      case AdvancementRuleType.KnockoutElimination =>
        s"Knockout seeding locks top $qualifiedCount players into the next bracket."
      case AdvancementRuleType.ScoreThreshold =>
        s"Score threshold keeps $qualifiedCount players at or above ${stage.advancementRule.thresholdScore.getOrElse(0)}."
      case AdvancementRuleType.Custom =>
        stage.advancementRule.note.getOrElse("Custom advancement policy requires manual adjudication.")

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
