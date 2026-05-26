package riichinexus.microservices.club.objects

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

  def fromString(value: String): Either[String, ClubApplicationStatus] =
    value.trim match
      case "Pending" => Right(ClubApplicationStatus.Pending)
      case "Approved" => Right(ClubApplicationStatus.Approved)
      case "Rejected" => Right(ClubApplicationStatus.Rejected)
      case "Withdrawn" => Right(ClubApplicationStatus.Withdrawn)
      case other => Left(s"Unsupported ClubApplicationStatus value: $other")
