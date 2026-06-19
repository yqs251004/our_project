package riichinexus.microservices.opsanalytics.domain.functions

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.tournament.objects.`private`.MatchRecordPrivateView
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuRound

/** DashboardFunctions 提供仪表盘相关的领域计算、校验和转换函数。 */

private[opsanalytics] object DashboardFunctions:
  def empty(owner: DashboardOwner, at: Instant): Dashboard =
    Dashboard(
      owner = owner,
      sampleSize = 0,
      dealInRate = 0.0,
      winRate = 0.0,
      averageWinPoints = 0.0,
      riichiRate = 0.0,
      averagePlacement = 0.0,
      topFinishRate = 0.0,
      lastUpdatedAt = at
    )

  def buildPlayerDashboard(
      playerId: PlayerId,
      records: Vector[MatchRecordPrivateView],
      rounds: Vector[PaifuRound],
      at: Instant,
      version: Int
  ): Dashboard =
    val playerResults = records.flatMap(_.seatResults.find(_.playerId == playerId))
    val roundStats = rounds.map(round => AdvancedStatsRoundAnalysis.buildRoundStats(round, playerId))
    val placements = playerResults.map(_.placement.toDouble)
    val topFinishes = playerResults.count(_.placement == 1)

    Dashboard(
      owner = DashboardOwner.Player(playerId),
      sampleSize = rounds.size,
      dealInRate = AdvancedStatsRoundAnalysis.ratio(roundStats.count(_.dealtIn), rounds.size),
      winRate = AdvancedStatsRoundAnalysis.ratio(roundStats.count(_.won), rounds.size),
      averageWinPoints = AdvancedStatsRoundAnalysis.average(roundStats.filter(_.won).map(_.resultDelta.toDouble)),
      riichiRate = AdvancedStatsRoundAnalysis.ratio(roundStats.count(_.riichiDeclared), rounds.size),
      averagePlacement = AdvancedStatsRoundAnalysis.average(placements),
      topFinishRate = AdvancedStatsRoundAnalysis.ratio(topFinishes, records.size),
      lastUpdatedAt = at,
      version = version
    )

  def buildClubDashboard(
      clubId: ClubId,
      memberDashboards: Vector[Dashboard],
      at: Instant,
      version: Int
  ): Dashboard =
    if memberDashboards.isEmpty then empty(DashboardOwner.Club(clubId), at).copy(version = version)
    else
      Dashboard(
        owner = DashboardOwner.Club(clubId),
        sampleSize = memberDashboards.map(_.sampleSize).sum,
        dealInRate = weightedAverage(memberDashboards, _.dealInRate),
        winRate = weightedAverage(memberDashboards, _.winRate),
        averageWinPoints = weightedAverage(memberDashboards, _.averageWinPoints),
        riichiRate = weightedAverage(memberDashboards, _.riichiRate),
        averagePlacement = weightedAverage(memberDashboards, _.averagePlacement),
        topFinishRate = weightedAverage(memberDashboards, _.topFinishRate),
        lastUpdatedAt = at,
        version = version
      )

  def ownerKey(owner: DashboardOwner): String =
    DashboardOwner.toString(owner)

  def ownerType(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(_) => "player"
      case DashboardOwner.Club(_)   => "club"

  def weightedAverage(dashboards: Vector[Dashboard], selector: Dashboard => Double): Double =
    val totalWeight = dashboards.map(_.sampleSize).sum
    if totalWeight <= 0 then 0.0
    else round2(dashboards.map(dashboard => selector(dashboard) * dashboard.sampleSize).sum / totalWeight.toDouble)

  def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
