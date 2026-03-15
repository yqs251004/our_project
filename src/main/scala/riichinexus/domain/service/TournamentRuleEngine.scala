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

  override def buildStageRanking(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerId],
      records: Vector[MatchRecord],
      at: Instant
  ): StageRankingSnapshot =
    val effectiveRecords = rankingRecords(stage, records)
    val placementPointMap = Map(1 -> 4, 2 -> 2, 3 -> 1, 4 -> 0)

    val statsByPlayer = effectiveRecords
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
      archivedTableCount = effectiveRecords.map(_.tableId).distinct.size,
      scheduledTableCount = stage.scheduledTableIds.size
    )

  override def projectAdvancement(
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
    val isKnockoutStage =
      stage.format == StageFormat.Knockout ||
        stage.format == StageFormat.Finals ||
        stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

    if !isKnockoutStage then
      throw IllegalArgumentException(
        s"Stage ${stage.id.value} is not configured as a knockout stage"
      )

    val qualified = resolveKnockoutSeeds(stage, advancement, participants)
    if qualified.isEmpty then
      throw IllegalArgumentException(
        s"Stage ${stage.id.value} does not have enough qualified players to build a knockout bracket"
      )
    if qualified.size < 2 then
      throw IllegalArgumentException(
        s"Stage ${stage.id.value} needs at least two qualified players to build a knockout bracket"
      )
    if qualified.size != nextPowerOfTwo(qualified.size) || qualified.size % 4 != 0 then
      throw IllegalArgumentException(
        s"Knockout progression currently requires 4, 8, 16, 32... qualified players; got ${qualified.size}"
      )

    val bracketSize = qualified.size
    val tableByMatchId = tables.flatMap(table => table.bracketMatchId.map(_ -> table)).toMap
    val recordByMatchId = tables.flatMap { table =>
      if table.status == TableStatus.Archived then
        table.bracketMatchId.flatMap { matchId =>
          table.matchRecordId.flatMap { recordId =>
            records.find(record => record.id == recordId && record.tableId == table.id).map(matchId -> _)
          }
        }
      else None
    }.toMap

    val firstRoundMatches = bracketSize / 4
    val firstRound = (0 until firstRoundMatches).toVector.map { index =>
      val matchId = matchIdFor(1, index + 1)
      hydrateMatch(
        baseMatch = KnockoutBracketMatch(
          id = matchId,
          roundNumber = 1,
          position = index + 1,
          lane = KnockoutLane.Championship,
          slots = firstRoundSeedIndices(bracketSize, firstRoundMatches, index).map { seed =>
            KnockoutBracketSlot(
              seed = seed,
              playerId = qualified.lift(seed - 1),
              bye = false
            )
          },
          sourceMatchIds = Vector.empty,
          advancementCount = advancementCountForRound(1, bracketSize),
          nextMatchId = nextMatchId(1, index + 1, bracketSize),
          unlocked = true
        ),
        table = tableByMatchId.get(matchId),
        record = recordByMatchId.get(matchId)
      )
    }

    val remainingRounds = buildRemainingRounds(
      currentRound = 2,
      bracketSize = bracketSize,
      lane = KnockoutLane.Championship,
      placementResolver = feeder => feeder.results.filter(_.advanced).sortBy(_.placement),
      sourcePlacements = Vector(1, 2),
      previousRound = firstRound,
      tableByMatchId = tableByMatchId,
      recordByMatchId = recordByMatchId
    )

    val championshipRounds =
      KnockoutBracketRound(1, roundLabel(1, bracketSize), firstRound) +: remainingRounds
    val bronzeRounds = buildBronzeRounds(stage, championshipRounds, tableByMatchId, recordByMatchId)
    val repechageRounds = buildRepechageRounds(stage, firstRound, tableByMatchId, recordByMatchId)
    val allRounds = championshipRounds ++ bronzeRounds ++ repechageRounds

    KnockoutBracketSnapshot(
      tournamentId = tournament.id,
      stageId = stage.id,
      generatedAt = at,
      bracketSize = bracketSize,
      qualifiedPlayerIds = qualified,
      rounds = allRounds,
      summary =
        s"Knockout progression seeded ${qualified.size} players using ${normalizedSeedingPolicy(stage)} and unlocked ${allRounds.flatMap(_.matches).count(_.unlocked)} matches."
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
        s"Knockout seeding locks top $qualifiedCount players into the next bracket using ${normalizedSeedingPolicy(stage)}."
      case AdvancementRuleType.ScoreThreshold =>
        s"Score threshold keeps $qualifiedCount players at or above ${stage.advancementRule.thresholdScore.getOrElse(0)}."
      case AdvancementRuleType.Custom =>
        parseCustomPolicy(stage.advancementRule, ranking.entries.size).summary match
          case "" => "Custom advancement policy requires declarative directives."
          case summary =>
            s"Custom advancement selected $qualifiedCount players using $summary."

  private def rankingRecords(
      stage: TournamentStage,
      records: Vector[MatchRecord]
  ): Vector[MatchRecord] =
    if stage.format != StageFormat.Swiss || stage.swissRule.forall(_.carryOverPoints) then records
    else
      val latestRound = records.map(_.stageRoundNumber).foldLeft(0)(math.max)
      if latestRound <= 0 then records
      else records.filter(_.stageRoundNumber == latestRound)

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

    val policy = directives.foldLeft(CustomAdvancementPolicy()) { (acc, directive) =>
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

  private def normalizedSeedingPolicy(stage: TournamentStage): String =
    stage.knockoutRule.map(_.seedingPolicy.trim.toLowerCase).getOrElse("rating") match
      case "elo"       => "rating"
      case "standings" => "ranking"
      case value       => value

  private def resolveKnockoutSeeds(
      stage: TournamentStage,
      advancement: StageAdvancementSnapshot,
      participants: Vector[Player]
  ): Vector[PlayerId] =
    val qualifiedIds = advancement.qualifiedPlayerIds.distinct
    val rankingIndex = advancement.standings.zipWithIndex.map { case (entry, index) =>
      entry.playerId -> index
    }.toMap
    val participantById = participants.map(player => player.id -> player).toMap

    normalizedSeedingPolicy(stage) match
      case "ranking" =>
        advancement.standings
          .filter(entry => qualifiedIds.contains(entry.playerId))
          .sortBy(entry => (entry.seed.getOrElse(Int.MaxValue), rankingIndex.getOrElse(entry.playerId, Int.MaxValue)))
          .map(_.playerId)
      case "rating" =>
        qualifiedIds.sortBy { playerId =>
          val player = participantById.get(playerId)
          (
            -player.map(_.elo.toLong).getOrElse(Long.MinValue),
            rankingIndex.getOrElse(playerId, Int.MaxValue),
            playerId.value
          )
        }
      case policy =>
        throw IllegalArgumentException(s"Unsupported knockout seeding policy: $policy")

  private def nextPowerOfTwo(value: Int): Int =
    Iterator.iterate(1)(_ * 2).dropWhile(_ < math.max(1, value)).next()

  private def log2(value: Int): Int =
    (math.log(value.toDouble) / math.log(2.0)).toInt

  private def matchIdFor(roundNumber: Int, position: Int): String =
    s"r$roundNumber-m$position"

  private def nextMatchId(
      roundNumber: Int,
      position: Int,
      bracketSize: Int
  ): Option[String] =
    val totalRounds = totalKnockoutRounds(bracketSize)
    if roundNumber >= totalRounds then None
    else Some(matchIdFor(roundNumber + 1, (position + 1) / 2))

  private def roundLabel(roundNumber: Int, bracketSize: Int): String =
    val playersInRound = bracketSize / math.pow(2, roundNumber - 1).toInt
    val matches = playersInRound / 4
    matches match
      case 1 => "Final"
      case 2 => "Semifinal"
      case 4 => "Quarterfinal"
      case _ => s"Round $roundNumber"

  private def totalKnockoutRounds(bracketSize: Int): Int =
    log2(bracketSize) - 1

  private def advancementCountForRound(roundNumber: Int, bracketSize: Int): Int =
    if roundNumber >= totalKnockoutRounds(bracketSize) then 0 else 2

  private def firstRoundSeedIndices(
      bracketSize: Int,
      firstRoundMatches: Int,
      index: Int
  ): Vector[Int] =
    Vector(
      index + 1,
      bracketSize - index,
      firstRoundMatches + index + 1,
      bracketSize - firstRoundMatches - index
    )

  private def buildRemainingRounds(
      currentRound: Int,
      bracketSize: Int,
      lane: KnockoutLane,
      placementResolver: KnockoutBracketMatch => Vector[KnockoutBracketResult],
      sourcePlacements: Vector[Int],
      previousRound: Vector[KnockoutBracketMatch],
      tableByMatchId: Map[String, Table],
      recordByMatchId: Map[String, MatchRecord]
  ): Vector[KnockoutBracketRound] =
    if previousRound.size <= 1 then Vector.empty
    else
      val currentMatches = previousRound
        .grouped(2)
        .zipWithIndex
        .map { case (feederPair, index) =>
          val matchId = matchIdFor(currentRound, index + 1)
          val unlocked = feederPair.forall(_.completed)
          val slots =
            if unlocked then
              feederPair.flatMap { feeder =>
                placementResolver(feeder).map { result =>
                  KnockoutBracketSlot(
                    seed = result.placement,
                    playerId = Some(result.playerId),
                    bye = false,
                    sourceMatchId = Some(feeder.id),
                    sourcePlacement = Some(result.placement)
                  )
                }
              }.toVector
            else
              feederPair.flatMap { feeder =>
                sourcePlacements.map { placement =>
                  KnockoutBracketSlot(
                    seed = placement,
                    playerId = None,
                    bye = false,
                    sourceMatchId = Some(feeder.id),
                    sourcePlacement = Some(placement)
                  )
                }
              }.toVector

          hydrateMatch(
            baseMatch = KnockoutBracketMatch(
              id = matchId,
              roundNumber = currentRound,
              position = index + 1,
              lane = lane,
              slots = slots,
              sourceMatchIds = feederPair.map(_.id).toVector,
              advancementCount = advancementCountForRound(currentRound, bracketSize),
              nextMatchId = nextMatchId(currentRound, index + 1, bracketSize),
              unlocked = unlocked
            ),
            table = tableByMatchId.get(matchId),
            record = recordByMatchId.get(matchId)
          )
        }
        .toVector

      KnockoutBracketRound(
        roundNumber = currentRound,
        label = roundLabel(currentRound, bracketSize),
        matches = currentMatches
      ) +: buildRemainingRounds(
        currentRound = currentRound + 1,
        bracketSize = bracketSize,
        lane = lane,
        placementResolver = placementResolver,
        sourcePlacements = sourcePlacements,
        previousRound = currentMatches,
        tableByMatchId = tableByMatchId,
        recordByMatchId = recordByMatchId
      )

  private def buildBronzeRounds(
      stage: TournamentStage,
      championshipRounds: Vector[KnockoutBracketRound],
      tableByMatchId: Map[String, Table],
      recordByMatchId: Map[String, MatchRecord]
  ): Vector[KnockoutBracketRound] =
    if !stage.knockoutRule.exists(_.thirdPlaceMatch) then Vector.empty
    else
      championshipRounds.lastOption.flatMap(_.matches.headOption) match
        case Some(_) if championshipRounds.size >= 2 =>
          val semifinalRound = championshipRounds(championshipRounds.size - 2)
          if semifinalRound.matches.size != 2 then Vector.empty
          else
            val matchId = "bronze-r1-m1"
            val unlocked = semifinalRound.matches.forall(_.completed)
            val slots =
              if unlocked then
                semifinalRound.matches.flatMap { feeder =>
                  feeder.results.filterNot(_.advanced).sortBy(_.placement).map { result =>
                    KnockoutBracketSlot(
                      seed = result.placement,
                      playerId = Some(result.playerId),
                      sourceMatchId = Some(feeder.id),
                      sourcePlacement = Some(result.placement)
                    )
                  }
                }
              else
                semifinalRound.matches.flatMap { feeder =>
                  Vector(3, 4).map { placement =>
                    KnockoutBracketSlot(
                      seed = placement,
                      playerId = None,
                      sourceMatchId = Some(feeder.id),
                      sourcePlacement = Some(placement)
                    )
                  }
                }

            Vector(
              KnockoutBracketRound(
                roundNumber = semifinalRound.roundNumber,
                label = "Bronze Final",
                matches = Vector(
                  hydrateMatch(
                    baseMatch = KnockoutBracketMatch(
                      id = matchId,
                      roundNumber = semifinalRound.roundNumber,
                      position = 1,
                      lane = KnockoutLane.Bronze,
                      slots = slots.toVector,
                      sourceMatchIds = semifinalRound.matches.map(_.id),
                      advancementCount = 0,
                      unlocked = unlocked
                    ),
                    table = tableByMatchId.get(matchId),
                    record = recordByMatchId.get(matchId)
                  )
                )
              )
            )
        case _ => Vector.empty

  private def buildRepechageRounds(
      stage: TournamentStage,
      firstRound: Vector[KnockoutBracketMatch],
      tableByMatchId: Map[String, Table],
      recordByMatchId: Map[String, MatchRecord]
  ): Vector[KnockoutBracketRound] =
    if !stage.knockoutRule.exists(_.repechageEnabled) then Vector.empty
    else
      val initialRepechageMatches = firstRound
        .grouped(2)
        .zipWithIndex
        .map { case (feederPair, index) =>
          val matchId = s"repechage-r1-m${index + 1}"
          val unlocked = feederPair.forall(_.completed)
          val slots =
            if unlocked then
              feederPair.flatMap { feeder =>
                feeder.results.filterNot(_.advanced).sortBy(_.placement).map { result =>
                  KnockoutBracketSlot(
                    seed = result.placement,
                    playerId = Some(result.playerId),
                    sourceMatchId = Some(feeder.id),
                    sourcePlacement = Some(result.placement)
                  )
                }
              }
            else
              feederPair.flatMap { feeder =>
                Vector(3, 4).map { placement =>
                  KnockoutBracketSlot(
                    seed = placement,
                    playerId = None,
                    sourceMatchId = Some(feeder.id),
                    sourcePlacement = Some(placement)
                  )
                }
              }

          hydrateMatch(
            baseMatch = KnockoutBracketMatch(
              id = matchId,
              roundNumber = 1,
              position = index + 1,
              lane = KnockoutLane.Repechage,
              slots = slots.toVector,
              sourceMatchIds = feederPair.map(_.id).toVector,
              advancementCount = if feederPair.size > 1 || firstRound.size > 2 then 2 else 0,
              nextMatchId = if firstRound.size > 2 then Some(s"repechage-r2-m${(index + 2) / 2}") else None,
              unlocked = unlocked
            ),
            table = tableByMatchId.get(matchId),
            record = recordByMatchId.get(matchId)
          )
        }
        .toVector

      if initialRepechageMatches.isEmpty then Vector.empty
      else
        KnockoutBracketRound(
          roundNumber = 1,
          label = "Repechage Round 1",
          matches = initialRepechageMatches
        ) +: buildRemainingRounds(
          currentRound = 2,
          bracketSize = initialRepechageMatches.size * 4,
          lane = KnockoutLane.Repechage,
          placementResolver = feeder => feeder.results.filter(_.advanced).sortBy(_.placement),
          sourcePlacements = Vector(1, 2),
          previousRound = initialRepechageMatches,
          tableByMatchId = tableByMatchId,
          recordByMatchId = recordByMatchId
        )

  private def hydrateMatch(
      baseMatch: KnockoutBracketMatch,
      table: Option[Table],
      record: Option[MatchRecord]
  ): KnockoutBracketMatch =
    val results = record.toVector.flatMap { matchRecord =>
      matchRecord.seatResults
        .sortBy(_.placement)
        .map { result =>
          KnockoutBracketResult(
            playerId = result.playerId,
            placement = result.placement,
            finalPoints = result.finalPoints,
            advanced = result.placement <= baseMatch.advancementCount
          )
        }
    }

    baseMatch.copy(
      tableId = table.map(_.id),
      completed = record.nonEmpty,
      results = results
    )
