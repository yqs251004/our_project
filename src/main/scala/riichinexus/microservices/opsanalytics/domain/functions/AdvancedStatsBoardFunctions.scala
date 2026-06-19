package riichinexus.microservices.opsanalytics.domain.functions

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.tournament.objects.`private`.MatchRecordPrivateView
import riichinexus.microservices.tournament.objects.paifumanagement.Paifu

/** AdvancedStatsBoardFunctions 提供高级统计看板相关的领域计算、校验和转换函数。 */

private[opsanalytics] object AdvancedStatsBoardFunctions:
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
      records: Vector[MatchRecordPrivateView],
      paifus: Vector[Paifu],
      at: Instant,
      version: Int
  ): AdvancedStatsBoard =
    AdvancedStatsRoundAnalysis.buildPlayerBoard(playerId, records, paifus, at).copy(version = version)

  def buildClubBoard(
      clubId: ClubId,
      memberBoards: Vector[AdvancedStatsBoard],
      at: Instant,
      version: Int
  ): AdvancedStatsBoard =
    AdvancedStatsRoundAnalysis.buildClubBoard(clubId, memberBoards, at).copy(version = version)
