package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AssignClubTitleRequest(
    playerId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: PlayerId =
    PlayerId(operatorId)

object AssignClubTitleRequest:
  given ReadWriter[AssignClubTitleRequest] = macroRW
