package riichinexus.microservices.club.objects.rankprivilegemanagement

import riichinexus.microservices.auth.objects.Permission
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class ClubPrivilegeDefinition(
    code: ClubPrivilegeCode,
    label: String,
    description: String,
    delegatedPermissions: Vector[Permission] = Vector.empty
) derives ReadWriter
