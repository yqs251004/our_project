package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** AssignClubAdminRequest 表示分配俱乐部管理员请求 的前端请求参数。 */

final case class AssignClubAdminRequest(
    playerId: String,
    operatorId: String
)

object AssignClubAdminRequest:
  given ReadWriter[AssignClubAdminRequest] = macroRW
