package riichinexus.microservices.club.objects.tournamentparticipation

/** 俱乐部相对某场赛事的参与状态。
  *
  * `Invited` 表示赛事已邀请但俱乐部尚未接受，`Participating` 表示俱乐部已进入赛事参与名单。
  */
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
