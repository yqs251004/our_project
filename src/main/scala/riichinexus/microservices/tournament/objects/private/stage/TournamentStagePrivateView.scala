package riichinexus.microservices.tournament.objects.`private`.stage

import riichinexus.microservices.tournament.objects.stage.StageStatus
import riichinexus.microservices.tournament.objects.identity.TournamentStageId

/** 赛事内部快照中的阶段读模型。
  *
  * 阶段内部视图保留顺序、状态和阵容提交记录，供阶段推进、排桌计划和俱乐部阵容校验使用。
  */
final case class TournamentStagePrivateView(
    id: TournamentStageId,
    name: String,
    order: Int,
    status: StageStatus,
    lineupSubmissions: Vector[StageLineupSubmissionPrivateView]
)
