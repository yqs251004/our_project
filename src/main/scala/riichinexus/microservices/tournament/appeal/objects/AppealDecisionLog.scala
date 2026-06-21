package riichinexus.microservices.tournament.appeal.objects

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** 申诉处理过程中的单条操作日志。
  *
  * 每条记录保存操作者、决策文本、发生时间和备注，用于前端展示审计轨迹，也让后续复盘能还原工单流转原因。
  */
final case class AppealDecisionLog(
    operatorId: PlayerId,
    decision: String,
    decidedAt: Instant,
    note: Option[String] = None
)
