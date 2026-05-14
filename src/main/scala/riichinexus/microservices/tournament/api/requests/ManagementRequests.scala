package riichinexus.microservices.tournament.api.requests

import riichinexus.domain.model.*
import upickle.default.*

final case class AssignTournamentAdminRequest(
    playerId: String,
    operatorId: String
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: PlayerId =
    PlayerId(operatorId)

object ManagementRequests:
  given ReadWriter[AssignTournamentAdminRequest] = macroRW
