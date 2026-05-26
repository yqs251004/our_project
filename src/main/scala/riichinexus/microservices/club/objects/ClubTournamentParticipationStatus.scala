package riichinexus.microservices.club.objects

enum ClubTournamentParticipationStatus:
  case Invited
  case Participating

object ClubTournamentParticipationStatus:

  def toString(status: ClubTournamentParticipationStatus): String =
    status match
      case ClubTournamentParticipationStatus.Invited => "Invited"
      case ClubTournamentParticipationStatus.Participating => "Participating"

  def fromString(value: String): Either[String, ClubTournamentParticipationStatus] =
    value.trim match
      case "Invited" => Right(ClubTournamentParticipationStatus.Invited)
      case "Participating" => Right(ClubTournamentParticipationStatus.Participating)
      case other => Left(s"Unsupported ClubTournamentParticipationStatus value: $other")
