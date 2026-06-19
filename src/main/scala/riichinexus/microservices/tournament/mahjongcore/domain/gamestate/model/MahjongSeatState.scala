package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongDiscard, MahjongMeld}
import riichinexus.microservices.tournament.objects.paifu.PaifuTile
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

import riichinexus.system.json.JsonCodecs.given
/** 后端内部的座位状态，包含玩家真实手牌、摸牌、副露和河牌等不可直接全量公开的信息。 */
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