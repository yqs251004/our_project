package riichinexus.microservices.tournament.objects.stage.ranking

import java.time.Instant

import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 某个赛事阶段当前排名的计算快照。
  *
  * 快照记录生成时间、排名条目和已归档/已排牌桌数量，供公开排名、晋级预览和阶段完成判断使用。
  */
final case class StageRankingSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    entries: Vector[StageStandingEntry],
    archivedTableCount: Int,
    scheduledTableCount: Int
)

object StageRankingSnapshot:
  given ReadWriter[StageRankingSnapshot] = macroRW
