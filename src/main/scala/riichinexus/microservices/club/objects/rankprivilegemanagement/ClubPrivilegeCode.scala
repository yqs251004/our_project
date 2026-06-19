package riichinexus.microservices.club.objects.rankprivilegemanagement

import upickle.default.{ReadWriter, readwriter}

/** ClubPrivilegeCode 枚举俱乐部权限Code 可使用的公开取值。 */

enum ClubPrivilegeCode(private val serialized: String):
  case PriorityLineup extends ClubPrivilegeCode("priority-lineup")
  case ApproveRoster extends ClubPrivilegeCode("approve-roster")
  case ManageBank extends ClubPrivilegeCode("manage-bank")

object ClubPrivilegeCode:
  given ReadWriter[ClubPrivilegeCode] =
    readwriter[String].bimap(toString, fromString)

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
