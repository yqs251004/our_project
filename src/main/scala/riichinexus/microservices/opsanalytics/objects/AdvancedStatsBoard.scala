package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

/** AdvancedStatsBoard 表示前后端共享的高级统计看板 数据结构，包含owner、sampleSize、defenseStability、ukeireExpectation、averageShantenImprovement、callAggressionRate等。 */

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
