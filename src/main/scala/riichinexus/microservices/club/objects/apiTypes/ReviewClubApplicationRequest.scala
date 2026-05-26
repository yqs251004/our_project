package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class ReviewClubApplicationRequest(
    operatorId: String,
    decision: String,
    playerId: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def player: Option[PlayerId] =
    playerId.map(PlayerId(_))

object ReviewClubApplicationRequest:
  given ReadWriter[ReviewClubApplicationRequest] = macroRW
