package riichinexus.microservices.tournament.objects.competition

/** TournamentStatus 枚举赛事状态 可使用的公开取值。 */

enum TournamentStatus:
  case Draft
  case RegistrationOpen
  case Scheduled
  case InProgress
  case Completed
  case Cancelled
  case Archived

object TournamentStatus:
  def toString(status: TournamentStatus): String =
    status.toString

  def fromString(value: String): TournamentStatus =
    TournamentStatus.valueOf(value)
