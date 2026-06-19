package riichinexus.microservices.opsanalytics.domain.functions

import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.opsanalytics.domain.model.{EloRatingConfig, RatingChange}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.player.domain.Player

private[opsanalytics] object RatingService:
  def calculateDeltas(
      players: Vector[Player],
      standings: Vector[MatchRecordSeatResult],
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

  private def tableVolatilityFactor(standings: Vector[MatchRecordSeatResult]): Double =
    val averageSwing =
      standings.map(_.scoreDelta.abs).sum.toDouble / math.max(1.0, standings.size.toDouble)
    (1.0 + averageSwing / 30000.0).min(1.35)

  private def logistic(value: Double): Double =
    1.0 / (1.0 + math.exp(-value))

  private def validateInputs(
      players: Vector[Player],
      standings: Vector[MatchRecordSeatResult],
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
