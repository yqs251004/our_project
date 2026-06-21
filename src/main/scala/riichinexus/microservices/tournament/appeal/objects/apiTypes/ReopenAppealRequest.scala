package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 将已解决或已驳回申诉重新打开的请求体。
  *
  * 重开必须记录操作者和原因，备注用于补充新的证据或说明为什么原裁定需要再次复核。
  */
final case class ReopenAppealRequest(
    operatorId: String,
    reason: String,
    note: Option[String] = None
)

object ReopenAppealRequest:
  given ReadWriter[ReopenAppealRequest] = macroRW
