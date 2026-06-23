package riichinexus.microservices.tournament.domain.paifu.model

import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.microservices.tournament.objects.paifu.Paifu

/** 牌谱归档时需要同步写回的牌桌、对局记录和牌谱变更组合。
  *
  * 归档服务使用该模型把一次归档事务中的三类落库结果作为整体传递。
  */
private[tournament] final case class ArchivedPaifuChange(
    table: Table,
    matchRecord: MatchRecord,
    paifu: Paifu
)
