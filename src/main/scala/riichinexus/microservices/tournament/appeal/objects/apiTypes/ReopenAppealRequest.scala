package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** ReopenAppealRequest 表示重开申诉请求 的前端请求参数。 */

final case class ReopenAppealRequest(
    operatorId: String,
    reason: String,
    note: Option[String] = None
)

object ReopenAppealRequest:
  given ReadWriter[ReopenAppealRequest] = macroRW
