package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 给俱乐部成员设置展示称号的请求体。
  *
  * 称号用于成员列表和公开资料展示；备注和操作者会进入变更记录，便于解释称号来源。
  */
final case class AssignClubTitleRequest(
    playerId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
)

object AssignClubTitleRequest:
  given ReadWriter[AssignClubTitleRequest] = macroRW
