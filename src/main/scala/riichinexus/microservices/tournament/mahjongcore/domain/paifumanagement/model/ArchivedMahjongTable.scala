package riichinexus.microservices.tournament.mahjongcore.domain.paifumanagement.model

import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.MahjongTableState
import riichinexus.microservices.tournament.objects.paifu.Paifu

/** 实时牌桌归档后同时产出的新桌面状态、牌谱和对局记录。 */
private[mahjongcore] final case class ArchivedMahjongTable(
    tableState: MahjongTableState,
    paifu: Paifu,
    matchRecord: MatchRecord
)
