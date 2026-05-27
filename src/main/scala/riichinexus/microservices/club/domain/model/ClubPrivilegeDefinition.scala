package riichinexus.microservices.club.domain.model

import riichinexus.domain.model.Permission

final case class ClubPrivilegeDefinition(
    code: String,
    label: String,
    description: String,
    delegatedPermissions: Vector[Permission] = Vector.empty
) derives CanEqual
