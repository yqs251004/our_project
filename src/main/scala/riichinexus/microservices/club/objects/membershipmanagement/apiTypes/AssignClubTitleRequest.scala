package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AssignClubTitleRequest 表示分配俱乐部称号请求 的前端请求参数。 */

final case class AssignClubTitleRequest(
    playerId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
)

object AssignClubTitleRequest:
  given ReadWriter[AssignClubTitleRequest] = macroRW
