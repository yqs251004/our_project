package riichinexus.microservices.tournament.objects.rulesmanagement.ranking

import java.time.Instant

import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** StageRankingSnapshot 表示前后端共享的阶段Ranking快照 数据结构，包含赛事 ID、阶段 ID、生成时间、entries、archivedTableCount、scheduledTableCount。 */

final case class StageRankingSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    entries: Vector[StageStandingEntry],
    archivedTableCount: Int,
    scheduledTableCount: Int
) derives ReadWriter
