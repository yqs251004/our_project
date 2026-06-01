package riichinexus.microservices.club.objects.rankprivilegemanagement

import riichinexus.domain.model.Permission
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ClubPrivilegeDefinition(
    code: ClubPrivilegeCode,
    label: String,
    description: String,
    delegatedPermissions: Vector[Permission] = Vector.empty
) derives CanEqual, ReadWriter
