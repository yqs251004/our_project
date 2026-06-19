package riichinexus.microservices.tournament.objects.settlementmanagement

import upickle.default.{ReadWriter, readwriter}

/** TournamentSettlementStatus 枚举赛事结算状态 可使用的公开取值。 */

enum TournamentSettlementStatus:
  case Draft
  case Finalized
  case Superseded

object TournamentSettlementStatus:
  given ReadWriter[TournamentSettlementStatus] =
    readwriter[String].bimap(_.toString, TournamentSettlementStatus.valueOf)
