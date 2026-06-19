package riichinexus.microservices.tournament.objects.finalization

/** TournamentSettlementStatus 枚举赛事结算状态 可使用的公开取值。 */

enum TournamentSettlementStatus:
  case Draft
  case Finalized
  case Superseded

object TournamentSettlementStatus:
  def toString(status: TournamentSettlementStatus): String =
    status.toString

  def fromString(value: String): TournamentSettlementStatus =
    TournamentSettlementStatus.valueOf(value)
