package riichinexus.microservices.opsanalytics.domain.functions

import riichinexus.microservices.opsanalytics.domain.model.{EloRatingConfig, RatingChange}
import riichinexus.microservices.tournament.objects.`private`.MatchRecordSeatResultPrivateView
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView

/** RatingService 提供评级服务 相关的领域计算、校验和转换函数。 */

private[opsanalytics] object RatingService:
  def calculateDeltas(
      players: Vector[PlayerPrivateView],
      standings: Vector[MatchRecordSeatResultPrivateView],
      config: EloRatingConfig = EloRatingConfig()
  ): Vector[RatingChange] =
    validateInputs(players, standings, config)

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

  private def tableVolatilityFactor(standings: Vector[MatchRecordSeatResultPrivateView]): Double =
    val averageSwing =
      standings.map(_.scoreDelta.abs).sum.toDouble / math.max(1.0, standings.size.toDouble)
    (1.0 + averageSwing / 30000.0).min(1.35)

  private def logistic(value: Double): Double =
    1.0 / (1.0 + math.exp(-value))

  private def validateInputs(
      players: Vector[PlayerPrivateView],
      standings: Vector[MatchRecordSeatResultPrivateView],
      config: EloRatingConfig
  ): Unit =
    if players.isEmpty then
      throw IllegalArgumentException("Cannot calculate rating deltas without players")
    if players.map(_.id).toSet != standings.map(_.playerId).toSet then
      throw IllegalArgumentException("Players and final standings must reference the same participants")
    if config.kFactor <= 0 then
      throw IllegalArgumentException("kFactor must be positive")
    if math.abs((config.placementWeight + config.scoreWeight + config.umaWeight) - 1.0) > 0.0001 then
      throw IllegalArgumentException("Rating weights must sum to 1.0")
