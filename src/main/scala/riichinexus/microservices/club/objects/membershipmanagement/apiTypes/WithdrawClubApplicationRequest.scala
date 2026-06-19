package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** WithdrawClubApplicationRequest 表示Withdraw俱乐部申请请求 的前端请求参数。 */

final case class WithdrawClubApplicationRequest(
    operatorId: Option[String] = None,
    note: Option[String] = None
)

object WithdrawClubApplicationRequest:
  given ReadWriter[WithdrawClubApplicationRequest] = macroRW
