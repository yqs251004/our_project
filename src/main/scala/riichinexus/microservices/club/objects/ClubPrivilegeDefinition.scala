package riichinexus.microservices.club.objects

import riichinexus.domain.model.{ClubPrivilegeDefinition as DomainClubPrivilegeDefinition}
import upickle.default.*

final case class ClubPrivilegeDefinition(
    code: String,
    label: String,
    description: String,
    delegatedPermissions: Vector[String]
) derives ReadWriter

object ClubPrivilegeDefinition:
  def fromDomain(definition: DomainClubPrivilegeDefinition): ClubPrivilegeDefinition =
    ClubPrivilegeDefinition(
      code = definition.code,
      label = definition.label,
      description = definition.description,
      delegatedPermissions = definition.delegatedPermissions.map(_.toString)
    )
