package riichinexus.microservices.club.objects

import upickle.default.*

enum ClubPrivilegeCode(val wireValue: String) derives CanEqual:
  case PriorityLineup extends ClubPrivilegeCode("priority-lineup")
  case ApproveRoster extends ClubPrivilegeCode("approve-roster")
  case ManageBank extends ClubPrivilegeCode("manage-bank")

  override def toString: String = wireValue

object ClubPrivilegeCode:
  given ReadWriter[ClubPrivilegeCode] =
    readwriter[String].bimap(_.wireValue, fromString)

  def normalize(value: String): String =
    value.trim.toLowerCase

  def fromString(value: String): ClubPrivilegeCode =
    val normalized = normalize(value)
    values
      .find(privilege => privilege.wireValue == normalized)
      .getOrElse(
        throw IllegalArgumentException(
          s"Unsupported ClubPrivilegeCode value: $value"
        )
      )
