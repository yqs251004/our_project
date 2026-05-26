package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class RemoveClubMemberRequest(
    operatorId: Option[String] = None
):
  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

object RemoveClubMemberRequest:
  given ReadWriter[RemoveClubMemberRequest] = macroRW
