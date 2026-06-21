package riichinexus.microservices.club.objects.relationmanagement.apiTypes

import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 直接更新俱乐部对外关系的管理请求。
  *
  * 与提交申请不同，该请求用于拥有权限的操作者立即写入目标俱乐部关系，并附带备注供审计追踪。
  */
final case class UpdateClubRelationRequest(
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
)

object UpdateClubRelationRequest:
  given ReadWriter[UpdateClubRelationRequest] = macroRW
