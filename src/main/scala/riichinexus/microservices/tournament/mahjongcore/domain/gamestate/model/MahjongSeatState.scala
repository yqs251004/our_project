package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongDiscard, MahjongMeld}
import riichinexus.microservices.tournament.objects.paifu.PaifuTile
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

import riichinexus.system.json.JsonCodecs.given

/** 实时麻将桌中单个座位的后端内部状态。
  *
  * 状态保存玩家真实手牌、摸牌、副露、河牌、立直/一发/振听标记和当前点数，转换公开视图时需要按观看者身份隐藏暗牌。
  */
final case class MahjongSeatState(
    seat: SeatWind,
    playerId: PlayerId,
    points: Int,
    handTiles: Vector[PaifuTile],
    drawTile: Option[PaifuTile],
    melds: Vector[MahjongMeld],
    river: Vector[MahjongDiscard],
    riichi: Boolean,
    ippatsu: Boolean,
    furiten: Boolean
)
