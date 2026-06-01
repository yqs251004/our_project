package riichinexus.microservices.opsanalytics.domain.functions

import java.time.Instant

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.tournament.domain.recordmanagement.model.MatchRecord
import riichinexus.microservices.tournament.objects.paifumanagement.Paifu

object AdvancedStatsBoardFunctions:
  val currentCalculatorVersion: Int = 2

  def empty(owner: DashboardOwner, at: Instant): AdvancedStatsBoard =
    AdvancedStatsBoard(
      owner = owner,
      sampleSize = 0,
      defenseStability = 0.0,
      ukeireExpectation = 0.0,
      averageShantenImprovement = 0.0,
      callAggressionRate = 0.0,
      riichiConversionRate = 0.0,
      pressureDefenseRate = 0.0,
      postRiichiFoldRate = 0.0,
      shantenTrajectory = Vector.empty,
      calculatorVersion = currentCalculatorVersion,
      strictRoundSampleSize = 0,
      exactUkeireSampleRate = 0.0,
      exactDefenseSampleRate = 0.0,
      lastUpdatedAt = at
    )

  def buildPlayerBoard(
      playerId: PlayerId,
      records: Vector[MatchRecord],
      paifus: Vector[Paifu],
      at: Instant,
      version: Int
  ): AdvancedStatsBoard =
    AdvancedStatsRoundAnalysis.buildPlayerBoard(playerId, records, paifus, at).copy(version = version)

  def buildClubBoard(
      club: Club,
      memberBoards: Vector[AdvancedStatsBoard],
      at: Instant,
      version: Int
  ): AdvancedStatsBoard =
    AdvancedStatsRoundAnalysis.buildClubBoard(club, memberBoards, at).copy(version = version)
