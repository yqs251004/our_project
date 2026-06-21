package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 从俱乐部移除成员时提交的管理请求。
  *
  * 目标成员通常由路由参数给出；请求体只保留操作者，用于审计谁发起了移除操作。
  */
final case class RemoveClubMemberRequest(
    operatorId: Option[String] = None
)

object RemoveClubMemberRequest:
  given ReadWriter[RemoveClubMemberRequest] = macroRW
