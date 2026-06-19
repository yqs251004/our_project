package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** RevokeClubHonorRequest 表示撤销俱乐部荣誉请求 的前端请求参数。 */

final case class RevokeClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None
)

object RevokeClubHonorRequest:
  given ReadWriter[RevokeClubHonorRequest] = macroRW
