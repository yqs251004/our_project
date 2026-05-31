package riichinexus.microservices.club.domain.model

import riichinexus.microservices.club.objects.ClubPrivilegeCode

final case class ClubRankNode(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[ClubPrivilegeCode] = Vector.empty
) derives CanEqual
