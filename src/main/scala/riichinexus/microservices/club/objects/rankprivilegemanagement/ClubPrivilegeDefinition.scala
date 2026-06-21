package riichinexus.microservices.club.objects.rankprivilegemanagement

import riichinexus.microservices.auth.objects.Permission
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 一个俱乐部权限编码的展示说明和后端委派权限。
  *
  * 前端用 label/description 告知管理员权限含义，后端用 `delegatedPermissions` 将俱乐部等级映射到可执行的系统权限。
  */
final case class ClubPrivilegeDefinition(
    code: ClubPrivilegeCode,
    label: String,
    description: String,
    delegatedPermissions: Vector[Permission] = Vector.empty
) derives ReadWriter
