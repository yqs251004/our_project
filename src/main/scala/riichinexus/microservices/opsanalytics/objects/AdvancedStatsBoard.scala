package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

/** 高级统计页面展示的玩家或俱乐部分析看板。
  *
  * 它聚合防守稳定性、有效进张期望、向听改善、副露倾向、立直转化和 exact 样本覆盖率，展示的是重算任务产出的最新版本。
  */
final case class AdvancedStatsBoard(
    owner: DashboardOwner,
    sampleSize: Int,
    defenseStability: Double,
    ukeireExpectation: Double,
    averageShantenImprovement: Double,
    callAggressionRate: Double,
    riichiConversionRate: Double,
    pressureDefenseRate: Double,
    postRiichiFoldRate: Double,
    shantenTrajectory: Vector[Double],
    calculatorVersion: Int,
    strictRoundSampleSize: Int,
    exactUkeireSampleRate: Double,
    exactDefenseSampleRate: Double,
    lastUpdatedAt: Instant,
    version: Int = 0
)
