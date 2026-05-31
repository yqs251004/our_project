package riichinexus.microservices.tournament.objects.tablemanagement.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpdateTableSeatStateRequest(
    operatorId: String,
    ready: Option[Boolean] = None,
    disconnected: Option[Boolean] = None,
    note: Option[String] = None
)

object UpdateTableSeatStateRequest:
  given ReadWriter[UpdateTableSeatStateRequest] = macroRW
