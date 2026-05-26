package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AddClubMemberRequest(
    playerId: String,
    operatorId: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

object AddClubMemberRequest:
  given ReadWriter[AddClubMemberRequest] = macroRW
