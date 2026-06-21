package riichinexus.microservices.opsanalytics.objects

import java.time.Instant

/** 普通运营分析仪表盘的汇总指标。
  *
  * 相比高级统计看板，它展示更通用的胜率、放铳率、平均和牌点、立直率和名次指标，可同时用于玩家和俱乐部概览。
  */
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
