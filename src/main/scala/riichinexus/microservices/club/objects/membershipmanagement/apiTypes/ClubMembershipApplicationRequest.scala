package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** ClubMembershipApplicationRequest 表示俱乐部成员资格申请请求 的前端请求参数。 */

final case class ClubMembershipApplicationRequest(
    displayName: String,
    message: Option[String] = None,
    operatorId: Option[String] = None
)

object ClubMembershipApplicationRequest:
  given ReadWriter[ClubMembershipApplicationRequest] = macroRW
