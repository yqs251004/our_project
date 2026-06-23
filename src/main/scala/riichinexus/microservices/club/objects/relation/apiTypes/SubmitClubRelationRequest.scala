package riichinexus.microservices.club.objects.relation.apiTypes

import riichinexus.microservices.club.objects.relation.ClubRelationKind
import riichinexus.system.json.ClubJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 发起俱乐部关系变更申请的请求体。
  *
  * 它记录操作者、目标俱乐部、期望关系和说明，适合需要对方或后台进一步确认的提交流程。
  */
final case class SubmitClubRelationRequest(
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
)

object SubmitClubRelationRequest:
  given ReadWriter[SubmitClubRelationRequest] = macroRW
