package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AddClubMemberRequest 表示添加俱乐部成员请求 的前端请求参数。 */

final case class AddClubMemberRequest(
    playerId: String,
    operatorId: Option[String] = None
)

object AddClubMemberRequest:
  given ReadWriter[AddClubMemberRequest] = macroRW
