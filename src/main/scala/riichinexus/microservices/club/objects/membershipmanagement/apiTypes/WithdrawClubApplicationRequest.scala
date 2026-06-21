package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 申请人撤回入会申请时提交的请求体。
  *
  * `operatorId` 允许后端记录实际撤回主体，`note` 用于保存用户或后台补充的撤回原因。
  */
final case class WithdrawClubApplicationRequest(
    operatorId: Option[String] = None,
    note: Option[String] = None
)

object WithdrawClubApplicationRequest:
  given ReadWriter[WithdrawClubApplicationRequest] = macroRW
