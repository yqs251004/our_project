package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class CreateClubRequest(
    name: String,
    creatorId: String
)

object CreateClubRequest:
  given ReadWriter[CreateClubRequest] = macroRW
