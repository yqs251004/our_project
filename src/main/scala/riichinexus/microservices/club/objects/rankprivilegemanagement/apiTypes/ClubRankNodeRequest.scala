package riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes

import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import upickle.default.{ReadWriter, macroRW}

/** ClubRankNodeRequest 表示俱乐部等级节点请求 的前端请求参数。 */

final case class ClubRankNodeRequest(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[ClubPrivilegeCode] = Vector.empty
)

object ClubRankNodeRequest:
  given ReadWriter[ClubRankNodeRequest] = macroRW
