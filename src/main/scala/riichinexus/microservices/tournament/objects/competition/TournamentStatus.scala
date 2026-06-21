package riichinexus.microservices.tournament.objects.competition

/** 赛事从创建到归档的生命周期状态。
  *
  * 状态决定赛事能否报名、发布、开赛、结算或继续编辑，是公开列表和运营后台共同使用的流程依据。
  */
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
