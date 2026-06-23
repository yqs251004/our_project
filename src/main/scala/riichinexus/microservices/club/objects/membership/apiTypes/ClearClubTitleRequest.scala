package riichinexus.microservices.club.objects.membership.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 清除成员俱乐部称号的请求体。
  *
  * 目标成员由接口路径确定；请求体记录操作者和可选说明，便于审计称号为什么被移除。
  */
final case class ClearClubTitleRequest(
    operatorId: String,
    note: Option[String] = None
)

object ClearClubTitleRequest:
  given ReadWriter[ClearClubTitleRequest] = macroRW
