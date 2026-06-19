package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AdjustClubPointPoolRequest 表示Adjust俱乐部点数池请求 的前端请求参数。 */

final case class AdjustClubPointPoolRequest(
    operatorId: String,
    delta: Int,
    note: Option[String] = None
)

object AdjustClubPointPoolRequest:
  given ReadWriter[AdjustClubPointPoolRequest] = macroRW
