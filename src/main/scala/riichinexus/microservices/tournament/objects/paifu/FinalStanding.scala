package riichinexus.microservices.tournament.objects.paifu

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

/** 整场对局结束后的玩家最终名次。
  *
  * 记录玩家座位、最终点、名次以及 uma/oka 修正，作为对局记录、排行榜和赛事结算的基础成绩。
  */
final case class FinalStanding(
    playerId: PlayerId,
    seat: SeatWind,
    finalPoints: Int,
    placement: Int,
    uma: Double = 0.0,
    oka: Double = 0.0
)
