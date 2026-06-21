package riichinexus.microservices.tournament.objects.stage.ranking

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 阶段排名快照中的单个玩家成绩行。
  *
  * 它汇总对局数、名次分、总分差、总最终点、平均顺位、晋级状态和种子号，是晋级规则和公开排名共用的输入。
  */
final case class StageStandingEntry(
    playerId: PlayerId,
    matchesPlayed: Int,
    placementPoints: Int,
    totalScoreDelta: Int,
    totalFinalPoints: Int,
    averagePlacement: Double,
    qualified: Boolean = false,
    seed: Option[Int] = None
) derives ReadWriter
