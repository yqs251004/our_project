package riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes

import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import upickle.default.*

final case class ClubRankNodeRequest(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[ClubPrivilegeCode] = Vector.empty
)

object ClubRankNodeRequest:
  given ReadWriter[ClubRankNodeRequest] = macroRW
