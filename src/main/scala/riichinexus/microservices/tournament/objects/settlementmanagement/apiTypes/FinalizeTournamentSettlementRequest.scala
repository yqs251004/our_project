package riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class FinalizeTournamentSettlementRequest(
    operatorId: String,
    note: Option[String] = None
)

object FinalizeTournamentSettlementRequest:
  given ReadWriter[FinalizeTournamentSettlementRequest] = macroRW
