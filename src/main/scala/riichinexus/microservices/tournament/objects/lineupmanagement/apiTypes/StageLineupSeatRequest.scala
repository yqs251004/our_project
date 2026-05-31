package riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind
import upickle.default.*

final case class StageLineupSeatRequest(
    playerId: String,
    preferredWind: Option[String] = None,
    reserve: Boolean = false
)

object StageLineupSeatRequest:
  given ReadWriter[StageLineupSeatRequest] = macroRW
