package riichinexus.microservices.club.objects.rankprivilegemanagement

/** ClubRankNode 表示前后端共享的俱乐部等级节点 数据结构，包含code、label、minimumContribution、privileges。 */

final case class ClubRankNode(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[ClubPrivilegeCode] = Vector.empty
)
