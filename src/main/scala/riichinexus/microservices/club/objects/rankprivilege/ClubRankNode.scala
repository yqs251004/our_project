package riichinexus.microservices.club.objects.rankprivilege

/** 俱乐部贡献等级树中的一个等级节点。
  *
  * 节点定义达到该等级所需的最低贡献和随等级授予的俱乐部权限，供成员权限快照与管理页共同使用。
  */
final case class ClubRankNode(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[ClubPrivilegeCode] = Vector.empty
)
