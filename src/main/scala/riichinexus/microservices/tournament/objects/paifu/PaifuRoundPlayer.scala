package riichinexus.microservices.tournament.objects.paifu

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

/** 牌谱小局中某位玩家的起手和个人事件轨迹。
  *
  * 它把玩家、座位风、起手牌和玩家视角事件流绑定在一起，供回放时重建每个座位的手牌变化。
  */
final case class PaifuRoundPlayer(
    playerId: PlayerId,
    seat: SeatWind,
    initialHand: PaifuHand,
    track: PaifuPlayerTrack
)
