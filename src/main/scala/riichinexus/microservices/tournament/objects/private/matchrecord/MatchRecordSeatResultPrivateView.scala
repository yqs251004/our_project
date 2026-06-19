package riichinexus.microservices.tournament.objects.`private`.matchrecord

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

/** MatchRecordSeatResultPrivateView 表示后端内部使用的对局记录座位结果后端内部视图 read model，包含玩家 ID、座位、俱乐部 ID、最终点数、名次、分数变化等。 */

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
