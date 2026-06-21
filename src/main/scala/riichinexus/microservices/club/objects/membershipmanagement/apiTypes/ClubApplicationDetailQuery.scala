package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 读取单个入会申请详情时附带的访问主体参数。
  *
  * 申请 ID 通常来自路径，`operatorId` 用于后端计算当前用户是否可以审核或撤回这条申请。
  */
final case class ClubApplicationDetailQuery(
    operatorId: Option[String] = None
)

object ClubApplicationDetailQuery:
  given ReadWriter[ClubApplicationDetailQuery] = macroRW
