package riichinexus.microservices.club.objects.relationmanagement.apiTypes

import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import upickle.default.{ReadWriter, macroRW}

/** SubmitClubRelationRequest 表示提交俱乐部关系请求 的前端请求参数。 */

final case class SubmitClubRelationRequest(
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
)

object SubmitClubRelationRequest:
  given ReadWriter[SubmitClubRelationRequest] = macroRW
