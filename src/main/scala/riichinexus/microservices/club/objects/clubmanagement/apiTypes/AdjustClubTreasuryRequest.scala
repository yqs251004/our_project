package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 调整俱乐部资金库余额的管理请求。
  *
  * `delta` 表示本次资金变动量，后端会结合操作者和备注生成审计记录，而不是让前端直接覆盖余额。
  */
final case class AdjustClubTreasuryRequest(
    operatorId: String,
    delta: Long,
    note: Option[String] = None
)

object AdjustClubTreasuryRequest:
  given ReadWriter[AdjustClubTreasuryRequest] = macroRW
