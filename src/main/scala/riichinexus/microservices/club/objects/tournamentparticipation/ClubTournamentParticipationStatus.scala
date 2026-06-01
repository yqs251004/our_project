package riichinexus.microservices.club.objects.tournamentparticipation

enum ClubTournamentParticipationStatus:
  case Invited
  case Participating

object ClubTournamentParticipationStatus:

  def toString(status: ClubTournamentParticipationStatus): String =
    status match
      case ClubTournamentParticipationStatus.Invited => "Invited"
      case ClubTournamentParticipationStatus.Participating => "Participating"

  def fromString(value: String): ClubTournamentParticipationStatus =
    value.trim match
      case "Invited" => ClubTournamentParticipationStatus.Invited
      case "Participating" => ClubTournamentParticipationStatus.Participating
      case other => throw IllegalArgumentException(s"Unsupported ClubTournamentParticipationStatus value: $other")
