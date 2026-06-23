package riichinexus.microservices.club.objects.membership.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 玩家向俱乐部提交入会申请时填写的请求体。
  *
  * `displayName` 固化申请时的展示名，`message` 是给俱乐部管理员看的申请说明，`operatorId` 可承载代提交场景。
  */
final case class ClubMembershipApplicationRequest(
    displayName: String,
    message: Option[String] = None,
    operatorId: Option[String] = None
)

object ClubMembershipApplicationRequest:
  given ReadWriter[ClubMembershipApplicationRequest] = macroRW
