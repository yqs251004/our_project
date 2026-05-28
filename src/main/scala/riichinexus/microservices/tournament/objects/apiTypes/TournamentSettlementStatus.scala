package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.microservices.tournament.domain.model.{TournamentSettlementStatus as DomainTournamentSettlementStatus}
import upickle.default.*

enum TournamentSettlementStatus derives CanEqual:
  case Draft
  case Finalized
  case Superseded

  def toDomain: DomainTournamentSettlementStatus =
    DomainTournamentSettlementStatus.valueOf(toString)

object TournamentSettlementStatus:
  given ReadWriter[TournamentSettlementStatus] =
    readwriter[String].bimap(_.toString, TournamentSettlementStatus.valueOf)

  def fromDomain(status: DomainTournamentSettlementStatus): TournamentSettlementStatus =
    TournamentSettlementStatus.valueOf(status.toString)
