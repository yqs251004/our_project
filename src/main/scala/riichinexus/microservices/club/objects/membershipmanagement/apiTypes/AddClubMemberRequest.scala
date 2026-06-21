package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 管理员手动把玩家加入俱乐部的请求体。
  *
  * `playerId` 是被加入的目标成员，`operatorId` 是执行该动作的人，省略时可由后端从当前会话补全。
  */
final case class AddClubMemberRequest(
    playerId: String,
    operatorId: Option[String] = None
)

object AddClubMemberRequest:
  given ReadWriter[AddClubMemberRequest] = macroRW
