package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRuleset, MahjongTableStatus, MahjongTableSticks}
import riichinexus.microservices.tournament.objects.paifu.PaifuRound
import riichinexus.microservices.tournament.objects.stage.table.TableId

import riichinexus.system.json.JsonCodecs.given
/** 后端内部保存的一张比赛桌实时麻将完整状态，包含暗牌、小局记录和乐观锁版本。 */
final case class MahjongTableState(
    tableId: TableId,
    ruleset: MahjongRuleset,
    status: MahjongTableStatus,
    seats: Vector[MahjongSeatState],
    currentRound: Option[MahjongRoundState],
    finishedRounds: Vector[PaifuRound],
    sticks: MahjongTableSticks,
    version: Int
)