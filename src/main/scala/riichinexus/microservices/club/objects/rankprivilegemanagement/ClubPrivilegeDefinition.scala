package riichinexus.microservices.club.objects.rankprivilegemanagement

import riichinexus.microservices.auth.objects.Permission
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** ClubPrivilegeDefinition 表示前后端共享的俱乐部权限Definition 数据结构，包含code、label、description、delegatedPermissions。 */

final case class ClubPrivilegeDefinition(
    code: ClubPrivilegeCode,
    label: String,
    description: String,
    delegatedPermissions: Vector[Permission] = Vector.empty
) derives ReadWriter
