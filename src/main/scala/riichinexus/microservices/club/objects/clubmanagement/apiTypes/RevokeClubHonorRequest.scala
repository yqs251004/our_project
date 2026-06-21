package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 从俱乐部公开荣誉中撤销指定称号的管理请求。
  *
  * 后端按标题定位要移除的荣誉，并记录操作者和可选原因，便于解释荣誉列表为何发生变化。
  */
final case class RevokeClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None
)

object RevokeClubHonorRequest:
  given ReadWriter[RevokeClubHonorRequest] = macroRW
