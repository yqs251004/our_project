package riichinexus.domain.service

import riichinexus.domain.model.*

final case class RatingChange(
    playerId: PlayerId,
    delta: Int
) derives CanEqual

trait RatingService:
  def calculateDeltas(
      players: Vector[Player],
      standings: Vector[MatchRecordSeatResult]
  ): Vector[RatingChange]

final case class EloRatingConfig(
    kFactor: Int = 36,
    placementWeight: Double = 0.6,
    scoreWeight: Double = 0.3,
    umaWeight: Double = 0.1
) derives CanEqual:
  require(kFactor > 0, "kFactor must be positive")
  require(
    math.abs((placementWeight + scoreWeight + umaWeight) - 1.0) <= 0.0001,
    "Rating weights must sum to 1.0"
  )

object EloRatingConfig:
  val default: EloRatingConfig = EloRatingConfig()

trait RatingConfigProvider:
  def current(): EloRatingConfig

final case class StaticRatingConfigProvider(
    config: EloRatingConfig = EloRatingConfig.default
) extends RatingConfigProvider:
  override def current(): EloRatingConfig =
    config

final class PairwiseEloRatingService(
    configProvider: RatingConfigProvider = StaticRatingConfigProvider()
) extends RatingService:
  override def calculateDeltas(
      players: Vector[Player],
      standings: Vector[MatchRecordSeatResult]
  ): Vector[RatingChange] =
    val config = configProvider.current()
    require(players.nonEmpty, "Cannot calculate rating deltas without players")
    require(
      players.map(_.id).toSet == standings.map(_.playerId).toSet,
      "Players and final standings must reference the same participants"
    )

    val standingByPlayer = standings.map(standing => standing.playerId -> standing).toMap
    val volatilityFactor = tableVolatilityFactor(standings)

    val rawDeltas = players.map { player =>
      val standing = standingByPlayer(player.id)
      val expectedScore = players
        .filterNot(_.id == player.id)
        .map { opponent =>
          expectedAgainst(player.elo, opponent.elo)
        }
        .sum / (players.size - 1).toDouble

      val actualScore =
        config.placementWeight * placementPerformance(standing.placement, players.size) +
          config.scoreWeight * scoreDeltaPerformance(standing.scoreDelta) +
          config.umaWeight * umaPerformance(standing.uma + standing.oka)

      player.id -> (config.kFactor * volatilityFactor * (actualScore - expectedScore))
    }

    val rounded = rawDeltas.map { case (playerId, delta) =>
      RatingChange(playerId, math.round(delta).toInt)
    }

    val drift = rounded.map(_.delta).sum
    if drift == 0 || rounded.isEmpty then rounded
    else
      val adjustmentIndex = rawDeltas.zipWithIndex.maxBy { case ((_, rawDelta), _) => math.abs(rawDelta) }._2
      val target = rounded(adjustmentIndex)
      rounded.updated(adjustmentIndex, target.copy(delta = target.delta - drift))

  private def expectedAgainst(playerElo: Int, opponentElo: Int): Double =
    1.0 / (1.0 + math.pow(10.0, (opponentElo - playerElo) / 400.0))

  private def placementPerformance(placement: Int, tableSize: Int): Double =
    if tableSize <= 1 then 1.0
    else (tableSize - placement).toDouble / (tableSize - 1).toDouble

  private def scoreDeltaPerformance(scoreDelta: Int): Double =
    logistic(scoreDelta.toDouble / 7000.0)

  private def umaPerformance(totalUma: Double): Double =
    logistic(totalUma / 15.0)

  private def tableVolatilityFactor(standings: Vector[MatchRecordSeatResult]): Double =
    val averageSwing =
      standings.map(_.scoreDelta.abs).sum.toDouble / math.max(1.0, standings.size.toDouble)
    (1.0 + averageSwing / 30000.0).min(1.35)

  private def logistic(value: Double): Double =
    1.0 / (1.0 + math.exp(-value))
