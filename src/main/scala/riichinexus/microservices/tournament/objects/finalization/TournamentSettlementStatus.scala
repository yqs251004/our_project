package riichinexus.microservices.tournament.objects.finalization

/** 赛事结算快照的生命周期状态。
  *
  * Draft 表示仍可调整，Finalized 表示已经确认发放，Superseded 表示该版本被新的结算修订取代。
  */
enum TournamentSettlementStatus:
  case Draft
  case Finalized
  case Superseded

object TournamentSettlementStatus:
  def toString(status: TournamentSettlementStatus): String =
    status.toString

  def fromString(value: String): TournamentSettlementStatus =
    TournamentSettlementStatus.valueOf(value)
