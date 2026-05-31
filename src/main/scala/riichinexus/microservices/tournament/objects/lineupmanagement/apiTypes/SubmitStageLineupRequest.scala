package riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class SubmitStageLineupRequest(
    clubId: String,
    operatorId: String,
    seats: Vector[StageLineupSeatRequest],
    note: Option[String] = None
)

object SubmitStageLineupRequest:
  given ReadWriter[SubmitStageLineupRequest] = macroRW
