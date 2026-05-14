package riichinexus.microservices.club.api.requests

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AssignClubAdminRequest(
    playerId: String,
    operatorId: String
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: PlayerId =
    PlayerId(operatorId)

object AssignClubAdminRequest:
  given ReadWriter[AssignClubAdminRequest] = macroRW
