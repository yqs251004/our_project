package riichinexus.microservices.tournament.appeal.objects

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** AppealDecisionLog 表示前后端共享的申诉裁定Log 数据结构，包含operatorId、decision、decidedAt、note。 */

final case class AppealDecisionLog(
    operatorId: PlayerId,
    decision: String,
    decidedAt: Instant,
    note: Option[String] = None
)
