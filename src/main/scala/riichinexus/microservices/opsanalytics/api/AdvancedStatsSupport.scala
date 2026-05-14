package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import scala.collection.mutable

import riichinexus.domain.model.*

private[riichinexus] object AdvancedStatsSupport:
  private val TerminalAndHonorIndices =
    Set(0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33)

  final case class ExactRoundStats(
      strictTileTrackable: Boolean,
      ukeireSamples: Vector[Int],
      postRiichiDiscardCount: Int,
      safePostRiichiDiscardCount: Int,
      foldDiscardCount: Int
  )

  final case class PlayerRoundStats(
      shantenPath: Vector[Int],
      won: Boolean,
      dealtIn: Boolean,
      resultDelta: Int,
      riichiDeclared: Boolean,
      callCount: Int,
      pressureResponseCount: Int,
      postRiichiDealIn: Boolean,
      foldLikeResponse: Boolean,
      shantenImprovement: Double,
      exactUkeireSamples: Vector[Int],
      exactDefenseSampleCount: Int,
      exactSafeDefenseCount: Int,
      exactFoldCount: Int,
      strictTileTrackable: Boolean
  )

  def buildPlayerBoard(
      playerId: PlayerId,
      records: Vector[MatchRecord],
      paifus: Vector[Paifu],
      at: Instant
  ): AdvancedStatsBoard =
    val rounds = paifus.flatMap(_.rounds)
    val roundStats = rounds.map(round => buildRoundStats(round, playerId))
    val placements = records
      .flatMap(_.seatResults.find(_.playerId == playerId))
      .map(_.placement.toDouble)
    val riichiRounds = roundStats.count(_.riichiDeclared)
    val pressureRounds = roundStats.count(_.pressureResponseCount > 0)
    val exactDefenseSamples = roundStats.map(_.exactDefenseSampleCount).sum
    val exactSafeDefenseSamples = roundStats.map(_.exactSafeDefenseCount).sum
    val exactFoldSamples = roundStats.map(_.exactDefenseSampleCount).sum
    val exactFoldCount = roundStats.map(_.exactFoldCount).sum
    val fallbackPressureDefenseRate =
      ratio(roundStats.count(stats => stats.pressureResponseCount > 0 && !stats.postRiichiDealIn), pressureRounds)
    val fallbackFoldRate =
      ratio(roundStats.count(_.foldLikeResponse), pressureRounds)

    AdvancedStatsBoard(
      owner = DashboardOwner.Player(playerId),
      sampleSize = rounds.size,
      defenseStability = calculateDefenseStability(roundStats, placements),
      ukeireExpectation = average(roundStats.map(calculateUkeireExpectation)),
      averageShantenImprovement = average(roundStats.map(_.shantenImprovement)),
      callAggressionRate = ratio(roundStats.count(_.callCount > 0), rounds.size),
      riichiConversionRate = ratio(roundStats.count(stats => stats.riichiDeclared && stats.won), riichiRounds),
      pressureDefenseRate =
        if exactDefenseSamples > 0 then ratio(exactSafeDefenseSamples, exactDefenseSamples)
        else fallbackPressureDefenseRate,
      postRiichiFoldRate =
        if exactFoldSamples > 0 then ratio(exactFoldCount, exactFoldSamples)
        else fallbackFoldRate,
      shantenTrajectory = aggregateTrajectory(roundStats.map(_.shantenPath.map(_.toDouble))),
      calculatorVersion = AdvancedStatsBoard.CurrentCalculatorVersion,
      strictRoundSampleSize = roundStats.count(_.strictTileTrackable),
      exactUkeireSampleRate = ratio(roundStats.count(_.exactUkeireSamples.nonEmpty), rounds.size),
      exactDefenseSampleRate = ratio(roundStats.count(_.exactDefenseSampleCount > 0), rounds.size),
      lastUpdatedAt = at
    )

  def buildClubBoard(
      club: Club,
      memberBoards: Vector[AdvancedStatsBoard],
      at: Instant
  ): AdvancedStatsBoard =
    if memberBoards.isEmpty then AdvancedStatsBoard.empty(DashboardOwner.Club(club.id), at)
    else
      AdvancedStatsBoard(
        owner = DashboardOwner.Club(club.id),
        sampleSize = memberBoards.map(_.sampleSize).sum,
        defenseStability = weightedAverage(memberBoards, _.sampleSize, _.defenseStability),
        ukeireExpectation = weightedAverage(memberBoards, _.sampleSize, _.ukeireExpectation),
        averageShantenImprovement = weightedAverage(memberBoards, _.sampleSize, _.averageShantenImprovement),
        callAggressionRate = weightedAverage(memberBoards, _.sampleSize, _.callAggressionRate),
        riichiConversionRate = weightedAverage(memberBoards, _.sampleSize, _.riichiConversionRate),
        pressureDefenseRate = weightedAverage(memberBoards, _.sampleSize, _.pressureDefenseRate),
        postRiichiFoldRate = weightedAverage(memberBoards, _.sampleSize, _.postRiichiFoldRate),
        shantenTrajectory = aggregateTrajectory(memberBoards.map(_.shantenTrajectory)),
        calculatorVersion = AdvancedStatsBoard.CurrentCalculatorVersion,
        strictRoundSampleSize = memberBoards.map(_.strictRoundSampleSize).sum,
        exactUkeireSampleRate = weightedAverage(memberBoards, _.sampleSize, _.exactUkeireSampleRate),
        exactDefenseSampleRate = weightedAverage(memberBoards, _.sampleSize, _.exactDefenseSampleRate),
        lastUpdatedAt = at
      )

  def buildRoundStats(round: KyokuRecord, playerId: PlayerId): PlayerRoundStats =
    val playerActions = round.actions.filter(_.actor.contains(playerId))
    val shantenPath = playerActions.flatMap(_.shantenAfterAction)
    val riichiDeclared = playerActions.exists(_.actionType == PaifuActionType.Riichi)
    val callCount = playerActions.count(action => isOpenCall(action.actionType))
    val externalRiichiSequence = round.actions.collectFirst {
      case action
          if action.actionType == PaifuActionType.Riichi && action.actor.exists(_ != playerId) =>
        action.sequenceNo
    }
    val pressureResponses =
      externalRiichiSequence.toVector.flatMap { sequenceNo =>
        playerActions.filter(_.sequenceNo > sequenceNo)
      }
    val dealtIn =
      round.result.outcome == HandOutcome.Ron && round.result.target.contains(playerId)
    val foldLikeResponse =
      pressureResponses.nonEmpty && !riichiDeclared && callCount == 0 && !dealtIn
    val shantenImprovement =
      (shantenPath.headOption, shantenPath.lastOption) match
        case (Some(initial), Some(terminal)) => math.max(0.0, initial.toDouble - terminal.toDouble)
        case _                              => 0.0
    val exactStats = analyzeRoundExactly(round, playerId)

    PlayerRoundStats(
      shantenPath = shantenPath,
      won = round.result.winner.contains(playerId),
      dealtIn = dealtIn,
      resultDelta = round.result.scoreChanges.find(_.playerId == playerId).map(_.delta).getOrElse(0),
      riichiDeclared = riichiDeclared,
      callCount = callCount,
      pressureResponseCount = pressureResponses.size,
      postRiichiDealIn =
        externalRiichiSequence.nonEmpty &&
          round.result.outcome == HandOutcome.Ron &&
          round.result.target.contains(playerId),
      foldLikeResponse = foldLikeResponse,
      shantenImprovement = shantenImprovement,
      exactUkeireSamples = exactStats.ukeireSamples,
      exactDefenseSampleCount = exactStats.postRiichiDiscardCount,
      exactSafeDefenseCount = exactStats.safePostRiichiDiscardCount,
      exactFoldCount = exactStats.foldDiscardCount,
      strictTileTrackable = exactStats.strictTileTrackable
    )

  def calculateDefenseStability(
      roundStats: Vector[PlayerRoundStats],
      placements: Vector[Double]
  ): Double =
    val pressureRounds = roundStats.count(_.pressureResponseCount > 0)
    val pressureHoldRounds = roundStats.count(stats => stats.pressureResponseCount > 0 && !stats.postRiichiDealIn)
    val exactDefenseSamples = roundStats.map(_.exactDefenseSampleCount).sum
    val exactSafeDefenseSamples = roundStats.map(_.exactSafeDefenseCount).sum
    val averageLossSeverity =
      average(roundStats.filter(_.resultDelta < 0).map(stats => math.abs(stats.resultDelta).toDouble))
    val placementStability = placementConsistency(placements)
    val lossControl = 1.0 - math.min(1.0, averageLossSeverity / 12000.0)
    val safeRoundRate = rawRatio(roundStats.count(_.resultDelta >= 0), roundStats.size)
    val pressureHoldRate =
      if exactDefenseSamples > 0 then rawRatio(exactSafeDefenseSamples, exactDefenseSamples)
      else rawRatio(pressureHoldRounds, pressureRounds)

    round2(
      clamp01(
        safeRoundRate * 0.25 +
          (1.0 - rawRatio(roundStats.count(_.dealtIn), roundStats.size)) * 0.4 +
          pressureHoldRate * 0.2 +
          lossControl * 0.1 +
          placementStability * 0.05
      )
    )

  def calculateUkeireExpectation(stats: PlayerRoundStats): Double =
    if stats.exactUkeireSamples.nonEmpty then average(stats.exactUkeireSamples.map(_.toDouble))
    else if stats.shantenPath.isEmpty then 0.0
    else
      val shantenPotential = stats.shantenPath.map(shanten => 14.0 - shanten.toDouble)
      val transitionBonuses = stats.shantenPath
        .zip(stats.shantenPath.drop(1))
        .map { case (previous, current) =>
          if current < previous then 1.5
          else if current == previous then 0.15
          else -0.85
        }
      val actionBonus =
        stats.callCount.toDouble * 0.25 +
          (if stats.riichiDeclared then 0.65 else 0.0) +
          (if stats.won then 0.3 else 0.0)
      round2(
        math.max(
          0.0,
          (shantenPotential.sum + transitionBonuses.sum + actionBonus) / stats.shantenPath.size.toDouble
        )
      )

  def aggregateTrajectory(trajectories: Vector[Vector[Double]]): Vector[Double] =
    val maxLength = trajectories.map(_.size).foldLeft(0)(math.max)

    (0 until maxLength).toVector.flatMap { index =>
      val samples = trajectories.flatMap(_.lift(index))
      if samples.isEmpty then None else Some(round2(samples.sum / samples.size.toDouble))
    }

  def ratio(numerator: Int, denominator: Int): Double =
    round2(rawRatio(numerator, denominator))

  def rawRatio(numerator: Int, denominator: Int): Double =
    if denominator <= 0 then 0.0 else numerator.toDouble / denominator.toDouble

  def average(values: Vector[Double]): Double =
    if values.isEmpty then 0.0 else round2(values.sum / values.size.toDouble)

  def weightedAverage[T](
      items: Vector[T],
      sampleSize: T => Int,
      selector: T => Double
  ): Double =
    val totalWeight = items.map(sampleSize).sum
    if totalWeight <= 0 then 0.0
    else round2(items.map(item => selector(item) * sampleSize(item)).sum / totalWeight.toDouble)

  def clamp01(value: Double): Double =
    math.max(0.0, math.min(1.0, value))

  def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

  private def analyzeRoundExactly(round: KyokuRecord, playerId: PlayerId): ExactRoundStats =
    val ukeireSamples = analyzeExactUkeire(round, playerId)
    val defenseStats = analyzeExactDefense(round, playerId)

    ExactRoundStats(
      strictTileTrackable = ukeireSamples.nonEmpty || defenseStats.postRiichiDiscardCount > 0,
      ukeireSamples = ukeireSamples,
      postRiichiDiscardCount = defenseStats.postRiichiDiscardCount,
      safePostRiichiDiscardCount = defenseStats.safePostRiichiDiscardCount,
      foldDiscardCount = defenseStats.foldDiscardCount
    )

  private def analyzeExactUkeire(round: KyokuRecord, playerId: PlayerId): Vector[Int] =
    round.initialHands.get(playerId).flatMap(parseHandCounts) match
      case None => Vector.empty
      case Some(initialCounts) if initialCounts.sum != 13 =>
        Vector.empty
      case Some(initialCounts) =>
        val visibleKnown = initialCounts.clone()
        var hand = initialCounts.clone()
        val samples = Vector.newBuilder[Int]
        var trackable = true

        round.actions.foreach { action =>
          if trackable then
            action.actor match
              case Some(actor) if actor == playerId =>
                val snapshotCounts = action.handTilesAfterAction.flatMap(parseHandCounts)
                snapshotCounts match
                  case Some(snapshot) =>
                    hand = snapshot
                    updateVisibleKnown(visibleKnown, publiclyRevealedTiles(action))
                    if hand.sum == 13 then
                      samples += calculateExactUkeire(hand.clone(), visibleKnown.clone())
                  case None =>
                    action.actionType match
                      case PaifuActionType.Draw =>
                        action.tile.flatMap(parseTile) match
                          case Some(tileIndex) =>
                            hand(tileIndex) += 1
                            visibleKnown(tileIndex) += 1
                          case None =>
                            trackable = false
                      case PaifuActionType.Discard | PaifuActionType.Riichi =>
                        action.tile.flatMap(parseTile) match
                          case Some(tileIndex) if hand(tileIndex) > 0 =>
                            hand(tileIndex) -= 1
                            updateVisibleKnown(visibleKnown, publiclyRevealedTiles(action))
                            if hand.sum == 13 then
                              samples += calculateExactUkeire(hand.clone(), visibleKnown.clone())
                          case None if action.actionType == PaifuActionType.Riichi =>
                            updateVisibleKnown(visibleKnown, publiclyRevealedTiles(action))
                          case _ =>
                            trackable = false
                      case callType if isMeldAction(callType) =>
                        trackable = false
                      case _ =>
                        updateVisibleKnown(visibleKnown, publiclyRevealedTiles(action))
              case _ =>
                updateVisibleKnown(visibleKnown, publiclyRevealedTiles(action))
        }

        if trackable then samples.result() else Vector.empty

  private def analyzeExactDefense(round: KyokuRecord, playerId: PlayerId): ExactRoundStats =
    val riichiDiscards = mutable.Map.empty[PlayerId, mutable.Set[Int]]
    var playerDeclaredRiichi = false
    var postRiichiDiscardCount = 0
    var safePostRiichiDiscardCount = 0
    var foldDiscardCount = 0
    val publicVisible = Array.fill(34)(0)

    round.actions.foreach { action =>
      action.actor match
        case Some(actor) if action.actionType == PaifuActionType.Riichi && actor != playerId =>
          val discards = riichiDiscards.getOrElseUpdate(actor, mutable.Set.empty)
          publiclyRevealedTiles(action).foreach { tileIndex =>
            discards += tileIndex
          }
          updateVisibleKnown(publicVisible, publiclyRevealedTiles(action))
        case Some(actor) if action.actionType == PaifuActionType.Discard && actor != playerId =>
          publiclyRevealedTiles(action).foreach { tileIndex =>
            riichiDiscards.get(actor).foreach(_ += tileIndex)
          }
          updateVisibleKnown(publicVisible, publiclyRevealedTiles(action))
        case Some(actor) if actor == playerId && action.actionType == PaifuActionType.Riichi =>
          playerDeclaredRiichi = true
          updateVisibleKnown(publicVisible, publiclyRevealedTiles(action))
        case Some(actor) if actor == playerId && isPlayerExposureAction(action.actionType) && riichiDiscards.nonEmpty =>
          val discardedTiles = publiclyRevealedTiles(action)
          discardedTiles.foreach { tileIndex =>
            postRiichiDiscardCount += 1
            val genbutsuSafe = riichiDiscards.values.forall(_.contains(tileIndex))
            val deadSafe = publicVisible(tileIndex) + 1 >= 4
            if genbutsuSafe || deadSafe then
              safePostRiichiDiscardCount += 1
              if !playerDeclaredRiichi then foldDiscardCount += 1
          }
          updateVisibleKnown(publicVisible, discardedTiles)
          if isMeldAction(action.actionType) then
            updateVisibleKnown(publicVisible, meldExposureOnly(action))
        case _ =>
          updateVisibleKnown(publicVisible, publiclyRevealedTiles(action))
    }

    ExactRoundStats(
      strictTileTrackable = postRiichiDiscardCount > 0,
      ukeireSamples = Vector.empty,
      postRiichiDiscardCount = postRiichiDiscardCount,
      safePostRiichiDiscardCount = safePostRiichiDiscardCount,
      foldDiscardCount = foldDiscardCount
    )

  private def calculateExactUkeire(
      handCounts: Array[Int],
      visibleKnown: Array[Int]
  ): Int =
    val currentShanten = calculateShanten(handCounts)

    (0 until 34).foldLeft(0) { (total, tileIndex) =>
      val remainingCopies = 4 - visibleKnown(tileIndex)
      if remainingCopies <= 0 then total
      else
        handCounts(tileIndex) += 1
        val improved = bestShantenAfterDiscard(handCounts) < currentShanten
        handCounts(tileIndex) -= 1
        if improved then total + remainingCopies else total
    }

  private def bestShantenAfterDiscard(counts: Array[Int]): Int =
    (0 until 34)
      .filter(counts(_) > 0)
      .map { tileIndex =>
        counts(tileIndex) -= 1
        val shanten = calculateShanten(counts)
        counts(tileIndex) += 1
        shanten
      }
      .foldLeft(8)(math.min)

  private def calculateShanten(counts: Array[Int]): Int =
    Vector(
      calculateRegularShanten(counts.clone()),
      calculateChiitoiShanten(counts),
      calculateKokushiShanten(counts)
    ).min

  private def calculateRegularShanten(counts: Array[Int]): Int =
    var best = 8

    def dfs(index: Int, melds: Int, pairs: Int, taatsu: Int): Unit =
      var nextIndex = index
      while nextIndex < 34 && counts(nextIndex) == 0 do nextIndex += 1

      val boundedTaatsu = math.min(taatsu, 4 - melds)
      best = math.min(best, 8 - melds * 2 - boundedTaatsu - pairs)

      if nextIndex >= 34 then ()
      else
        if counts(nextIndex) >= 3 then
          counts(nextIndex) -= 3
          dfs(nextIndex, melds + 1, pairs, taatsu)
          counts(nextIndex) += 3

        if isSuitTile(nextIndex) && tileNumber(nextIndex) <= 7 &&
            counts(nextIndex + 1) > 0 && counts(nextIndex + 2) > 0 then
          counts(nextIndex) -= 1
          counts(nextIndex + 1) -= 1
          counts(nextIndex + 2) -= 1
          dfs(nextIndex, melds + 1, pairs, taatsu)
          counts(nextIndex) += 1
          counts(nextIndex + 1) += 1
          counts(nextIndex + 2) += 1

        if counts(nextIndex) >= 2 then
          if pairs == 0 then
            counts(nextIndex) -= 2
            dfs(nextIndex, melds, pairs + 1, taatsu)
            counts(nextIndex) += 2

          counts(nextIndex) -= 2
          dfs(nextIndex, melds, pairs, taatsu + 1)
          counts(nextIndex) += 2

        if isSuitTile(nextIndex) && tileNumber(nextIndex) <= 8 && counts(nextIndex + 1) > 0 then
          counts(nextIndex) -= 1
          counts(nextIndex + 1) -= 1
          dfs(nextIndex, melds, pairs, taatsu + 1)
          counts(nextIndex) += 1
          counts(nextIndex + 1) += 1

        if isSuitTile(nextIndex) && tileNumber(nextIndex) <= 7 && counts(nextIndex + 2) > 0 then
          counts(nextIndex) -= 1
          counts(nextIndex + 2) -= 1
          dfs(nextIndex, melds, pairs, taatsu + 1)
          counts(nextIndex) += 1
          counts(nextIndex + 2) += 1

        dfs(nextIndex + 1, melds, pairs, taatsu)

    dfs(0, 0, 0, 0)
    best

  private def calculateChiitoiShanten(counts: Array[Int]): Int =
    val pairCount = counts.count(_ >= 2)
    val uniqueCount = counts.count(_ > 0)
    6 - pairCount + math.max(0, 7 - uniqueCount)

  private def calculateKokushiShanten(counts: Array[Int]): Int =
    val uniqueCount = TerminalAndHonorIndices.count(index => counts(index) > 0)
    val pairExists = TerminalAndHonorIndices.exists(index => counts(index) >= 2)
    13 - uniqueCount - (if pairExists then 1 else 0)

  private def parseHandCounts(tiles: Vector[String]): Option[Array[Int]] =
    val counts = Array.fill(34)(0)
    val parsed = tiles.map(parseTile)
    if parsed.exists(_.isEmpty) then None
    else
      parsed.flatten.foreach { tileIndex =>
        counts(tileIndex) += 1
      }
      Some(counts)

  private def parseTile(tile: String): Option[Int] =
    if tile == null || tile.length != 2 then None
    else
      val numberChar = tile.charAt(0)
      val suitChar = tile.charAt(1)
      val normalizedNumber =
        if numberChar == '0' then 5
        else if numberChar.isDigit then numberChar.asDigit
        else -1

      suitChar match
        case 'm' if normalizedNumber >= 1 && normalizedNumber <= 9 =>
          Some(normalizedNumber - 1)
        case 'p' if normalizedNumber >= 1 && normalizedNumber <= 9 =>
          Some(9 + normalizedNumber - 1)
        case 's' if normalizedNumber >= 1 && normalizedNumber <= 9 =>
          Some(18 + normalizedNumber - 1)
        case 'z' if normalizedNumber >= 1 && normalizedNumber <= 7 =>
          Some(27 + normalizedNumber - 1)
        case _ =>
          None

  private def placementConsistency(placements: Vector[Double]): Double =
    if placements.isEmpty then 0.0
    else
      val mean = placements.sum / placements.size.toDouble
      val variance = placements.map(value => math.pow(value - mean, 2)).sum / placements.size.toDouble
      clamp01(1.0 - math.sqrt(variance) / 1.5)

  private def isSuitTile(index: Int): Boolean =
    index < 27

  private def tileNumber(index: Int): Int =
    (index % 9) + 1

  private def isOpenCall(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Chi | PaifuActionType.Pon | PaifuActionType.Kan | PaifuActionType.OpenKan =>
        true
      case _ =>
        false

  private def isMeldAction(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Chi | PaifuActionType.Pon | PaifuActionType.Kan |
          PaifuActionType.OpenKan | PaifuActionType.ClosedKan | PaifuActionType.AddedKan =>
        true
      case _ =>
        false

  private def isPlayerExposureAction(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Discard | PaifuActionType.Riichi | PaifuActionType.Chi |
          PaifuActionType.Pon | PaifuActionType.Kan | PaifuActionType.OpenKan |
          PaifuActionType.ClosedKan | PaifuActionType.AddedKan =>
        true
      case _ =>
        false

  private def isPublicExposure(actionType: PaifuActionType): Boolean =
    actionType match
      case PaifuActionType.Discard | PaifuActionType.Riichi | PaifuActionType.DoraReveal |
          PaifuActionType.Win | PaifuActionType.DrawGame | PaifuActionType.Chi |
          PaifuActionType.Pon | PaifuActionType.Kan | PaifuActionType.OpenKan |
          PaifuActionType.ClosedKan | PaifuActionType.AddedKan =>
        true
      case _ =>
        false

  private def publiclyRevealedTiles(action: PaifuAction): Vector[Int] =
    val rawTiles =
      if action.revealedTiles.nonEmpty then action.revealedTiles
      else if isPublicExposure(action.actionType) then action.tile.toVector
      else Vector.empty

    rawTiles.flatMap(parseTile)

  private def meldExposureOnly(action: PaifuAction): Vector[Int] =
    if action.revealedTiles.nonEmpty then action.revealedTiles.flatMap(parseTile)
    else Vector.empty

  private def updateVisibleKnown(visibleKnown: Array[Int], tileIndices: Vector[Int]): Unit =
    tileIndices.foreach { tileIndex =>
      visibleKnown(tileIndex) = math.min(4, visibleKnown(tileIndex) + 1)
    }
