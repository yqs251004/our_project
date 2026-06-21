package riichinexus.microservices.tournament.domain.matchrecord.model


import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

import riichinexus.system.json.JsonCodecs.given

/** 对局归档中单个座位的最终成绩。
  *
  * 结果同时记录玩家、座位风、可选俱乐部归属、终局点数、名次、分差以及 uma/oka 调整，供阶段积分和俱乐部贡献统计使用。
  */
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
