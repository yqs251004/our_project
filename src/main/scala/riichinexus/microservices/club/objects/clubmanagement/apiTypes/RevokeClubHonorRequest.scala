package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class RevokeClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None
)

object RevokeClubHonorRequest:
  given ReadWriter[RevokeClubHonorRequest] = macroRW
