package riichinexus.microservices.club.objects.rankprivilegemanagement

/** 俱乐部等级可以授予成员的权限编码。
  *
  * 这些编码用于等级树、成员权限快照和前端工作台展示，序列化值保持短横线格式以便作为稳定 API 协议。
  */
enum ClubPrivilegeCode(private val serialized: String):
  case PriorityLineup extends ClubPrivilegeCode("priority-lineup")
  case ApproveRoster extends ClubPrivilegeCode("approve-roster")
  case ManageBank extends ClubPrivilegeCode("manage-bank")

object ClubPrivilegeCode:
  def toString(code: ClubPrivilegeCode): String =
    code.serialized

  def fromString(value: String): ClubPrivilegeCode =
    val normalized = value.trim.toLowerCase
    values
      .find(toString(_) == normalized)
      .getOrElse(
        throw IllegalArgumentException(
          s"Unsupported ClubPrivilegeCode value: $value"
        )
      )
