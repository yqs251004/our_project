package riichinexus.microservices.club.objects.rankprivilege.apiTypes

import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode
import riichinexus.system.json.ClubJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 更新等级树时提交的单个等级节点。
  *
  * 请求节点使用与领域节点相同的 code、label、最低贡献和权限集合，让前端可以一次提交完整等级树。
  */
final case class ClubRankNodeRequest(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[ClubPrivilegeCode] = Vector.empty
)

object ClubRankNodeRequest:
  given ReadWriter[ClubRankNodeRequest] = macroRW
