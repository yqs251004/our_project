package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class RevokeClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object RevokeClubHonorRequest:
  given ReadWriter[RevokeClubHonorRequest] = macroRW
