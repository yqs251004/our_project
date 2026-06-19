package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

/** Dashboard 表示前后端共享的仪表盘 数据结构，包含owner、sampleSize、dealInRate、winRate、averageWinPoints、riichiRate等。 */

final case class Dashboard(
    owner: DashboardOwner,
    sampleSize: Int,
    dealInRate: Double,
    winRate: Double,
    averageWinPoints: Double,
    riichiRate: Double,
    averagePlacement: Double,
    topFinishRate: Double,
    lastUpdatedAt: Instant,
    version: Int = 0
)
