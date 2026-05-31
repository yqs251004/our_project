package riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class SettlementAdjustmentRequest(
    playerId: String,
    label: String,
    amount: Long,
    note: Option[String] = None
)

object SettlementAdjustmentRequest:
  given ReadWriter[SettlementAdjustmentRequest] = macroRW
