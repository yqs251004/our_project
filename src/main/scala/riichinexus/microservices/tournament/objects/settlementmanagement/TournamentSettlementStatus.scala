package riichinexus.microservices.tournament.objects.settlementmanagement

import upickle.default.*

enum TournamentSettlementStatus:
  case Draft
  case Finalized
  case Superseded

object TournamentSettlementStatus:
  given ReadWriter[TournamentSettlementStatus] =
    readwriter[String].bimap(_.toString, TournamentSettlementStatus.valueOf)
