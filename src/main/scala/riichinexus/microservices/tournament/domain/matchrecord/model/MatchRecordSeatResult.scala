package riichinexus.microservices.tournament.domain.matchrecord.model


import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

import riichinexus.system.json.JsonCodecs.given
/** MatchRecordSeatResult 表示后端领域中的对局记录座位结果状态或规则，包含玩家 ID、座位、俱乐部 ID、最终点数、名次、分数变化等。 */
final case class MatchRecordSeatResult(
    playerId: PlayerId,
    seat: SeatWind,
    clubId: Option[ClubId] = None,
    finalPoints: Int,
    placement: Int,
    scoreDelta: Int,
    uma: Double = 0.0,
    oka: Double = 0.0
)