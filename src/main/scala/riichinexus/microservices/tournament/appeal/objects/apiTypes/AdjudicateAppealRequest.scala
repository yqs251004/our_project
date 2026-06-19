package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.objects.{AppealDecisionType, AppealTableResolution}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AdjudicateAppealRequest 表示裁定申诉请求 的前端请求参数。 */

final case class AdjudicateAppealRequest(
    operatorId: String,
    decision: AppealDecisionType,
    verdict: String,
    tableResolution: Option[AppealTableResolution] = None,
    note: Option[String] = None
)

object AdjudicateAppealRequest:
  given ReadWriter[AdjudicateAppealRequest] = macroRW
