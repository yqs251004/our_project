package riichinexus.microservices.tournament.objects.finalization.apiTypes

import riichinexus.microservices.tournament.objects.finalization.TournamentSettlementStatus

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentStageId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 查询赛事结算快照列表时使用的过滤和分页参数。
  *
  * 可以按阶段、状态或冠军筛选，便于后台查看结算草稿、最终稿以及被新修订替代的历史版本。
  */
final case class TournamentSettlementQuery(
    stageId: Option[TournamentStageId] = None,
    status: Option[TournamentSettlementStatus] = None,
    championId: Option[PlayerId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
