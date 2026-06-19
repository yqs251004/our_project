package riichinexus.microservices.tournament.objects.settlementmanagement

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId

/** TournamentSettlementEntry 表示前后端共享的赛事结算条目 数据结构，包含玩家 ID、rank、awardAmount、baseAwardAmount、adjustmentAmount、deductionAmount等。 */

final case class TournamentSettlementEntry(
    playerId: PlayerId,
    rank: Int,
    awardAmount: Long,
    baseAwardAmount: Long,
    adjustmentAmount: Long = 0L,
    deductionAmount: Long = 0L,
    clubId: Option[ClubId] = None,
    clubShareAmount: Long = 0L,
    playerRetainedAmount: Long = 0L,
    finalPoints: Int,
    champion: Boolean = false
)
