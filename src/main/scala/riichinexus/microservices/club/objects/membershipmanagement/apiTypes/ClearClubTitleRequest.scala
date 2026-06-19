package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** ClearClubTitleRequest 表示Clear俱乐部称号请求 的前端请求参数。 */

final case class ClearClubTitleRequest(
    operatorId: String,
    note: Option[String] = None
)

object ClearClubTitleRequest:
  given ReadWriter[ClearClubTitleRequest] = macroRW
