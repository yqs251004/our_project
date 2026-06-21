package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongMeld, MahjongRuleset}
import riichinexus.microservices.tournament.objects.paifu.PaifuTile
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

import riichinexus.system.json.JsonCodecs.given

/** 役种与点数分析所需的和牌上下文。
  *
  * 它把赢家、放铳/自摸目标、座位风、场风、手牌、副露、和牌牌、宝牌、立直/一发/海底等状态和规则集集中传入分析函数。
  */
final case class MahjongWinContext(
    winner: PlayerId,
    target: Option[PlayerId],
    seatByPlayer: Map[PlayerId, SeatWind],
    roundWind: SeatWind,
    handTiles: Vector[PaifuTile],
    melds: Vector[MahjongMeld],
    winningTile: PaifuTile,
    doraIndicators: Vector[PaifuTile],
    uraDoraIndicators: Vector[PaifuTile] = Vector.empty,
    riichi: Boolean = false,
    doubleRiichi: Boolean = false,
    ippatsu: Boolean = false,
    rinshan: Boolean = false,
    haitei: Boolean = false,
    houtei: Boolean = false,
    tenhou: Boolean = false,
    chiihou: Boolean = false,
    ruleset: MahjongRuleset = MahjongRuleset()
)
