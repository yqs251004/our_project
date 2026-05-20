package riichinexus.domain.service

import riichinexus.domain.service.AdvancedStatsRoundAnalysis.PlayerRoundStats

private[service] object AdvancedStatsMetrics:
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

  private def placementConsistency(placements: Vector[Double]): Double =
    if placements.isEmpty then 0.0
    else
      val mean = placements.sum / placements.size.toDouble
      val variance = placements.map(value => math.pow(value - mean, 2)).sum / placements.size.toDouble
      clamp01(1.0 - math.sqrt(variance) / 1.5)
