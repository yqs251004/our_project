package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ReopenAppealRequest(
    operatorId: String,
    reason: String,
    note: Option[String] = None
)

object ReopenAppealRequest:
  given ReadWriter[ReopenAppealRequest] = macroRW
