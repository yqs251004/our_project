package riichinexus.microservices.club.domain.model

import riichinexus.domain.model.Permission
import riichinexus.microservices.club.objects.ClubPrivilegeCode

object ClubPrivilegeRegistry:
  private val definitionsByCode: Map[ClubPrivilegeCode, ClubPrivilegeDefinition] = Map(
    ClubPrivilegeCode.PriorityLineup ->
      ClubPrivilegeDefinition(
        code = ClubPrivilegeCode.PriorityLineup,
        label = "Priority Lineup",
        description = "Allows the member to claim protected lineup priority when stage seats are limited."
      ),
    ClubPrivilegeCode.ApproveRoster ->
      ClubPrivilegeDefinition(
        code = ClubPrivilegeCode.ApproveRoster,
        label = "Approve Roster",
        description = "Allows the member to approve roster-style club operations delegated by club admins.",
        delegatedPermissions = Vector(Permission.ManageClubMembership)
      ),
    ClubPrivilegeCode.ManageBank ->
      ClubPrivilegeDefinition(
        code = ClubPrivilegeCode.ManageBank,
        label = "Manage Bank",
        description = "Allows the member to adjust treasury and point-pool operations delegated by club admins.",
        delegatedPermissions = Vector(Permission.ManageClubOperations)
      )
  )

  val definitions: Vector[ClubPrivilegeDefinition] =
    ClubPrivilegeCode.values.toVector.map(definitionsByCode)

  private val definitionsByNormalizedCode: Map[String, ClubPrivilegeDefinition] =
    definitions.map(definition => definition.code.wireValue -> definition).toMap

  def normalize(value: String): String =
    ClubPrivilegeCode.normalize(value)

  def definitionFor(code: String): Option[ClubPrivilegeDefinition] =
    definitionsByNormalizedCode.get(normalize(code))

  def definitionFor(code: ClubPrivilegeCode): Option[ClubPrivilegeDefinition] =
    definitionsByCode.get(code)

  def requireSupported(code: String): ClubPrivilegeCode =
    val normalized = normalize(code)
    definitionFor(normalized)
      .map(_.code)
      .getOrElse(
        throw IllegalArgumentException(
          s"Unsupported club privilege code '$code'. Supported codes: ${supportedCodes.mkString(", ")}"
        )
      )

  def supportedCodes: Vector[String] =
    definitions.map(_.code.wireValue)
