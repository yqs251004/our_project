package riichinexus.domain.service

import java.time.Instant

import riichinexus.domain.model.*

private[riichinexus] object AdvancedStatsRoundAnalysis:
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
    val callCount = playerActions.count(action => AdvancedStatsExactAnalyzer.isOpenCall(action.actionType))
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
    val exactStats = AdvancedStatsExactAnalyzer.analyzeRound(round, playerId)

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
    AdvancedStatsMetrics.calculateDefenseStability(roundStats, placements)

  def calculateUkeireExpectation(stats: PlayerRoundStats): Double =
    AdvancedStatsMetrics.calculateUkeireExpectation(stats)

  def aggregateTrajectory(trajectories: Vector[Vector[Double]]): Vector[Double] =
    AdvancedStatsMetrics.aggregateTrajectory(trajectories)

  def ratio(numerator: Int, denominator: Int): Double =
    AdvancedStatsMetrics.ratio(numerator, denominator)

  def rawRatio(numerator: Int, denominator: Int): Double =
    AdvancedStatsMetrics.rawRatio(numerator, denominator)

  def average(values: Vector[Double]): Double =
    AdvancedStatsMetrics.average(values)

  def weightedAverage[T](
      items: Vector[T],
      sampleSize: T => Int,
      selector: T => Double
  ): Double =
    AdvancedStatsMetrics.weightedAverage(items, sampleSize, selector)

  def clamp01(value: Double): Double =
    AdvancedStatsMetrics.clamp01(value)

  def round2(value: Double): Double =
    AdvancedStatsMetrics.round2(value)
