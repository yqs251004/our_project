package riichinexus.microservices.tournament.objects.stage.table

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId

/** 牌桌上一名玩家的座位分配和准备状态。
  *
  * 该类型记录座位风、玩家、起始点、断线/准备标记和俱乐部归属，供牌桌准备页、实时对局和记录归档复用。
  */
final case class TableSeat(
    seat: SeatWind,
    playerId: PlayerId,
    initialPoints: Int = 25000,
    disconnected: Boolean = false,
    ready: Boolean = false,
    clubId: Option[ClubId] = None
)
