package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.CreateTournamentStageRequest
import upickle.default.*

final case class CreateTournamentRequest(
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    stages: Vector[CreateTournamentStageRequest],
    adminId: Option[String] = None
)

object CreateTournamentRequest:
  given ReadWriter[CreateTournamentRequest] = macroRW
