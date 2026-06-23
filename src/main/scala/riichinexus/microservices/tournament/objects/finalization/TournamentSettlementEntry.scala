package riichinexus.microservices.tournament.objects.finalization

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId

/** 赛事结算快照中某位玩家的最终分配结果。
  *
  * 条目记录名次、总奖金、基础奖金、调整/扣款、俱乐部分成、玩家留存金额和最终分数，是发放与公示的最小单位。
  */
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
