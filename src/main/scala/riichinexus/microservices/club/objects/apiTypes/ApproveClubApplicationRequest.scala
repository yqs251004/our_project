package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class ApproveClubApplicationRequest(
    playerId: String,
    operatorId: String,
    note: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: PlayerId =
    PlayerId(operatorId)

object ApproveClubApplicationRequest:
  given ReadWriter[ApproveClubApplicationRequest] = macroRW
