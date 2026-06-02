package riichinexus.microservices.club.objects.rankprivilegemanagement

import upickle.default.*

final case class ClubRankNode(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[ClubPrivilegeCode] = Vector.empty
) derives ReadWriter
