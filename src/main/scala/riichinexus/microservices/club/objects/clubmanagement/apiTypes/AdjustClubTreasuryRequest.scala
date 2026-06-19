package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AdjustClubTreasuryRequest 表示Adjust俱乐部资金库请求 的前端请求参数。 */

final case class AdjustClubTreasuryRequest(
    operatorId: String,
    delta: Long,
    note: Option[String] = None
)

object AdjustClubTreasuryRequest:
  given ReadWriter[AdjustClubTreasuryRequest] = macroRW
