package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongMeld, MahjongRuleset}
import riichinexus.microservices.tournament.objects.paifu.PaifuTile
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

import riichinexus.system.json.JsonCodecs.given
/** MahjongWinContext 表示后端领域中的麻将和牌上下文状态或规则，包含winner、target、seatByPlayer、roundWind、handTiles、melds等。 */
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