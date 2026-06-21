package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.objects.{AppealDecisionType, AppealTableResolution}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 裁定一张申诉工单时提交的请求体。
  *
  * 请求携带操作者、裁定动作、结论文本和可选牌桌处理方案，用于同时更新工单状态与受影响的牌桌流程。
  */
final case class AdjudicateAppealRequest(
    operatorId: String,
    decision: AppealDecisionType,
    verdict: String,
    tableResolution: Option[AppealTableResolution] = None,
    note: Option[String] = None
)

object AdjudicateAppealRequest:
  given ReadWriter[AdjudicateAppealRequest] = macroRW
