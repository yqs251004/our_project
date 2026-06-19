package riichinexus.microservices.club.objects.relationmanagement.apiTypes

import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** UpdateClubRelationRequest 表示更新俱乐部关系请求 的前端请求参数。 */

final case class UpdateClubRelationRequest(
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
)

object UpdateClubRelationRequest:
  given ReadWriter[UpdateClubRelationRequest] = macroRW
