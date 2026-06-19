package riichinexus.microservices.club.objects.membershipmanagement

/** ClubApplicationStatus 枚举俱乐部申请状态 可使用的公开取值。 */

enum ClubApplicationStatus:
  case Pending
  case Approved
  case Rejected
  case Withdrawn

object ClubApplicationStatus:

  def toString(status: ClubApplicationStatus): String =
    status match
      case ClubApplicationStatus.Pending => "Pending"
      case ClubApplicationStatus.Approved => "Approved"
      case ClubApplicationStatus.Rejected => "Rejected"
      case ClubApplicationStatus.Withdrawn => "Withdrawn"

  def fromString(value: String): ClubApplicationStatus =
    value.trim match
      case "Pending" => ClubApplicationStatus.Pending
      case "Approved" => ClubApplicationStatus.Approved
      case "Rejected" => ClubApplicationStatus.Rejected
      case "Withdrawn" => ClubApplicationStatus.Withdrawn
      case other => throw IllegalArgumentException(s"Unsupported ClubApplicationStatus value: $other")
