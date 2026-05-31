package riichinexus.microservices.tournament.objects

import upickle.default.*

enum TournamentSettlementStatus derives CanEqual:
  case Draft
  case Finalized
  case Superseded

object TournamentSettlementStatus:
  given ReadWriter[TournamentSettlementStatus] =
    readwriter[String].bimap(_.toString, TournamentSettlementStatus.valueOf)
