package riichinexus.microservices.tournament.objects.finalization

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** 赛事结算中针对单个玩家的人工奖惩调整。
  *
  * 调整会叠加到基础奖金计算结果上，`label` 和 `note` 用于解释加扣款来源，例如申诉裁定、处罚或额外奖励。
  */
final case class TournamentSettlementAdjustment(
    playerId: PlayerId,
    label: String,
    amount: Long,
    note: Option[String] = None
)
