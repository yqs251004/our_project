package riichinexus.microservices.club.objects.membership.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 授予俱乐部管理员身份的请求体。
  *
  * `playerId` 是被提升的成员，`operatorId` 是执行授权的人，后端会据此校验当前操作者是否有委派管理权限。
  */
final case class AssignClubAdminRequest(
    playerId: String,
    operatorId: String
)

object AssignClubAdminRequest:
  given ReadWriter[AssignClubAdminRequest] = macroRW
