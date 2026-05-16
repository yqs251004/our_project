package riichinexus.domain.service

import java.time.Instant

import riichinexus.domain.model.*

private[service] object TournamentAdvancementProjector:
  private final case class CustomAdvancementPolicy(
      topCount: Option[Int] = None,
      topPercent: Option[Double] = None,
      minMatches: Option[Int] = None,
      minPlacementPoints: Option[Int] = None,
      minScoreDelta: Option[Int] = None,
      minFinalPoints: Option[Int] = None,
      maxAveragePlacement: Option[Double] = None,
      reserveCount: Option[Int] = None,
      targetTableCount: Option[Int] = None
  ):
    def qualifyingLimit(totalEntries: Int): Int =
      topCount
        .orElse(targetTableCount.map(_ * 4))
        .orElse(topPercent.map(percent => math.ceil(totalEntries.toDouble * percent / 100.0).toInt))
        .getOrElse(totalEntries)

    def summary: String =
      Vector(
        topCount.map(value => s"top=$value"),
        topPercent.map(value => s"topPercent=${round2(value)}"),
        targetTableCount.map(value => s"targetTables=$value"),
        minMatches.map(value => s"minMatches=$value"),
        minPlacementPoints.map(value => s"placementPoints>=$value"),
        minScoreDelta.map(value => s"scoreDelta>=$value"),
        minFinalPoints.map(value => s"finalPoints>=$value"),
        maxAveragePlacement.map(value => s"averagePlacement<=${round2(value)}"),
        reserveCount.map(value => s"reserve=$value")
      ).flatten.mkString(", ")

  def project(
      tournament: Tournament,
      stage: TournamentStage,
      ranking: StageRankingSnapshot,
      at: Instant
  ): StageAdvancementSnapshot =
    val (qualifiedEntries, reserveIds, summaryText) =
      stage.advancementRule.ruleType match
        case AdvancementRuleType.SwissCut =>
          val qualifiedCount = stage.advancementRule.cutSize.getOrElse(defaultSwissCut(ranking.entries.size))
          val qualified = ranking.entries.take(math.max(0, math.min(qualifiedCount, ranking.entries.size)))
          val reserves = ranking.entries.filterNot(entry => qualified.exists(_.playerId == entry.playerId)).take(4).map(_.playerId)
          (qualified, reserves, summaryFor(stage, qualified.size, ranking))
        case AdvancementRuleType.KnockoutElimination =>
          val qualifiedCount = stage.knockoutRule.flatMap(_.bracketSize)
            .orElse(stage.advancementRule.targetTableCount.map(_ * 4))
            .getOrElse(math.min(4, ranking.entries.size))
          val qualified = ranking.entries.take(math.max(0, math.min(qualifiedCount, ranking.entries.size)))
          val reserves = ranking.entries.filterNot(entry => qualified.exists(_.playerId == entry.playerId)).take(4).map(_.playerId)
          (qualified, reserves, summaryFor(stage, qualified.size, ranking))
        case AdvancementRuleType.ScoreThreshold =>
          val qualified = ranking.entries.filter(_.totalScoreDelta >= stage.advancementRule.thresholdScore.getOrElse(0))
          val reserves = ranking.entries.filterNot(entry => qualified.exists(_.playerId == entry.playerId)).take(4).map(_.playerId)
          (qualified, reserves, summaryFor(stage, qualified.size, ranking))
        case AdvancementRuleType.Custom =>
          val customPolicy = parseCustomPolicy(stage.advancementRule, ranking.entries.size)
          val filteredEntries = ranking.entries.filter(entry => matchesCustomPolicy(entry, customPolicy))
          val qualified = filteredEntries.take(math.max(0, math.min(customPolicy.qualifyingLimit(filteredEntries.size), filteredEntries.size)))
          val reserveCount = customPolicy.reserveCount.getOrElse(math.min(4, ranking.entries.size))
          val reserves = ranking.entries.filterNot(entry => qualified.exists(_.playerId == entry.playerId)).take(reserveCount).map(_.playerId)
          val summary =
            s"Custom advancement selected ${qualified.size} players from ${filteredEntries.size} eligible entries using ${customPolicy.summary}."
          (qualified, reserves, summary)

    val qualifiedIds = qualifiedEntries.map(_.playerId)

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
      summary = summaryText
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
        s"Knockout seeding locks top $qualifiedCount players into the next bracket using ${TournamentKnockoutBracketBuilder.normalizedSeedingPolicy(stage)}."
      case AdvancementRuleType.ScoreThreshold =>
        s"Score threshold keeps $qualifiedCount players at or above ${stage.advancementRule.thresholdScore.getOrElse(0)}."
      case AdvancementRuleType.Custom =>
        parseCustomPolicy(stage.advancementRule, ranking.entries.size).summary match
          case "" => "Custom advancement policy requires declarative directives."
          case summary =>
            s"Custom advancement selected $qualifiedCount players using $summary."

  private def parseCustomPolicy(
      rule: AdvancementRule,
      totalEntries: Int
  ): CustomAdvancementPolicy =
    val note = rule.note.map(_.trim).filter(_.nonEmpty).getOrElse(
      throw IllegalArgumentException("Custom advancement rules require note directives")
    )

    val directives = note
      .split("[;\n,]+")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)

    if directives.isEmpty then
      throw IllegalArgumentException("Custom advancement rules require at least one directive")

    val basePolicy = CustomAdvancementPolicy(targetTableCount = rule.targetTableCount)

    val policy = directives.foldLeft(basePolicy) { (acc, directive) =>
      parseDirective(acc, directive, totalEntries)
    }

    if policy.summary.isEmpty then
      throw IllegalArgumentException(
        s"Custom advancement note '$note' did not contain any recognized directives"
      )

    policy

  private def parseDirective(
      policy: CustomAdvancementPolicy,
      directive: String,
      totalEntries: Int
  ): CustomAdvancementPolicy =
    val normalized = directive.replace(" ", "")

    if normalized.contains(">=") then
      val Array(rawKey, rawValue) = normalized.split(">=", 2)
      val key = normalizeDirectiveKey(rawKey)
      key match
        case "matches" | "minmatches" =>
          policy.copy(minMatches = Some(parseIntDirective(rawValue, directive)))
        case "placementpoints" | "minplacementpoints" =>
          policy.copy(minPlacementPoints = Some(parseIntDirective(rawValue, directive)))
        case "scoredelta" | "score" | "minscoredelta" =>
          policy.copy(minScoreDelta = Some(parseIntDirective(rawValue, directive)))
        case "finalpoints" | "minfinalpoints" =>
          policy.copy(minFinalPoints = Some(parseIntDirective(rawValue, directive)))
        case other =>
          throw IllegalArgumentException(s"Unsupported custom advancement directive '$other' in '$directive'")
    else if normalized.contains("<=") then
      val Array(rawKey, rawValue) = normalized.split("<=", 2)
      val key = normalizeDirectiveKey(rawKey)
      key match
        case "averageplacement" | "avgplacement" | "placementavg" =>
          policy.copy(maxAveragePlacement = Some(parseDoubleDirective(rawValue, directive)))
        case other =>
          throw IllegalArgumentException(s"Unsupported custom advancement directive '$other' in '$directive'")
    else if normalized.contains("=") then
      val Array(rawKey, rawValue) = normalized.split("=", 2)
      val key = normalizeDirectiveKey(rawKey)
      key match
        case "top" | "cut" | "qualify" =>
          policy.copy(topCount = Some(parsePositiveIntDirective(rawValue, directive)))
        case "toppercent" | "percent" =>
          val percent = parseDoubleDirective(rawValue, directive)
          require(percent > 0.0 && percent <= 100.0, s"Custom directive '$directive' must be within (0, 100]")
          policy.copy(topPercent = Some(percent))
        case "reserve" | "reserves" =>
          policy.copy(reserveCount = Some(math.max(0, parseIntDirective(rawValue, directive))))
        case "targettables" | "tables" =>
          val target = parsePositiveIntDirective(rawValue, directive)
          require(target * 4 <= math.max(totalEntries, 4), s"Custom directive '$directive' exceeds available participants")
          policy.copy(targetTableCount = Some(target))
        case other =>
          throw IllegalArgumentException(s"Unsupported custom advancement directive '$other' in '$directive'")
    else
      throw IllegalArgumentException(s"Unsupported custom advancement directive syntax '$directive'")

  private def normalizeDirectiveKey(value: String): String =
    value.toLowerCase.replace("-", "").replace("_", "")

  private def parseIntDirective(rawValue: String, directive: String): Int =
    rawValue.toIntOption.getOrElse(
      throw IllegalArgumentException(s"Custom directive '$directive' requires an integer value")
    )

  private def parsePositiveIntDirective(rawValue: String, directive: String): Int =
    val value = parseIntDirective(rawValue, directive)
    require(value > 0, s"Custom directive '$directive' must be positive")
    value

  private def parseDoubleDirective(rawValue: String, directive: String): Double =
    rawValue.toDoubleOption.getOrElse(
      throw IllegalArgumentException(s"Custom directive '$directive' requires a numeric value")
    )

  private def matchesCustomPolicy(
      entry: StageStandingEntry,
      policy: CustomAdvancementPolicy
  ): Boolean =
    policy.minMatches.forall(entry.matchesPlayed >= _) &&
      policy.minPlacementPoints.forall(entry.placementPoints >= _) &&
      policy.minScoreDelta.forall(entry.totalScoreDelta >= _) &&
      policy.minFinalPoints.forall(entry.totalFinalPoints >= _) &&
      policy.maxAveragePlacement.forall(entry.averagePlacement <= _)

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
