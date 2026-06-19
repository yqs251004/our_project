package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

/** PaifuRoundPlayer 表示前后端共享的牌谱小局玩家 数据结构，包含玩家 ID、座位、initialHand、track。 */

final case class PaifuRoundPlayer(
    playerId: PlayerId,
    seat: SeatWind,
    initialHand: PaifuHand,
    track: PaifuPlayerTrack
)
