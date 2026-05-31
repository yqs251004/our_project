package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AssignTournamentAdminRequest(
    playerId: String,
    operatorId: String
)

object AssignTournamentAdminRequest:
  given ReadWriter[AssignTournamentAdminRequest] = macroRW
