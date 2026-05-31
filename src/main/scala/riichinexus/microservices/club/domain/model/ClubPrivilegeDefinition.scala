package riichinexus.microservices.club.domain.model

import riichinexus.domain.model.Permission
import riichinexus.microservices.club.objects.ClubPrivilegeCode

final case class ClubPrivilegeDefinition(
    code: ClubPrivilegeCode,
    label: String,
    description: String,
    delegatedPermissions: Vector[Permission] = Vector.empty
) derives CanEqual
