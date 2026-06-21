package riichinexus.microservices.tournament.objects.`private`.matchrecord

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

/** 内部对局记录中单个座位的成绩结果。
  *
  * 使用强类型玩家、俱乐部和座位风，便于结算和统计计算直接关联玩家身份、俱乐部归属和名次分差。
  */
final case class MatchRecordSeatResultPrivateView(
    playerId: PlayerId,
    seat: SeatWind,
    clubId: Option[ClubId],
    finalPoints: Int,
    placement: Int,
    scoreDelta: Int,
    uma: Double,
    oka: Double
)
