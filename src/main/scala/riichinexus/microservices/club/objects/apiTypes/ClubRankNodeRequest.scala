package riichinexus.microservices.club.objects.apiTypes

import riichinexus.microservices.club.domain.model.ClubRankNode
import riichinexus.microservices.club.objects.ClubPrivilegeCode
import upickle.default.*

final case class ClubRankNodeRequest(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[ClubPrivilegeCode] = Vector.empty
)

object ClubRankNodeRequest:
  given ReadWriter[ClubRankNodeRequest] = macroRW
