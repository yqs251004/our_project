package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** RemoveClubMemberRequest 表示移除俱乐部成员请求 的前端请求参数。 */

final case class RemoveClubMemberRequest(
    operatorId: Option[String] = None
)

object RemoveClubMemberRequest:
  given ReadWriter[RemoveClubMemberRequest] = macroRW
