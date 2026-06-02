package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongMeld, MahjongRuleset}
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuTile
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

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
    ruleset: MahjongRuleset = MahjongRuleset()
)
