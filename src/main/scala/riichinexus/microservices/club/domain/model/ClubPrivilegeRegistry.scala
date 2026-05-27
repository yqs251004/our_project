package riichinexus.microservices.club.domain.model

import riichinexus.domain.model.Permission

object ClubPrivilegeRegistry:
  private val definitionsByCode: Map[ClubPrivilegeCode, ClubPrivilegeDefinition] = Map(
    ClubPrivilegeCode.PriorityLineup ->
      ClubPrivilegeDefinition(
        code = "priority-lineup",
        label = "Priority Lineup",
        description = "Allows the member to claim protected lineup priority when stage seats are limited."
      ),
    ClubPrivilegeCode.ApproveRoster ->
      ClubPrivilegeDefinition(
        code = "approve-roster",
        label = "Approve Roster",
        description = "Allows the member to approve roster-style club operations delegated by club admins.",
        delegatedPermissions = Vector(Permission.ManageClubMembership)
      ),
    ClubPrivilegeCode.ManageBank ->
      ClubPrivilegeDefinition(
        code = "manage-bank",
        label = "Manage Bank",
        description = "Allows the member to adjust treasury and point-pool operations delegated by club admins.",
        delegatedPermissions = Vector(Permission.ManageClubOperations)
      )
  )

  val definitions: Vector[ClubPrivilegeDefinition] =
    ClubPrivilegeCode.values.toVector.map(definitionsByCode)

  private val definitionsByNormalizedCode: Map[String, ClubPrivilegeDefinition] =
    definitions.map(definition => normalize(definition.code) -> definition).toMap

  def normalize(value: String): String =
    value.trim.toLowerCase

  def definitionFor(code: String): Option[ClubPrivilegeDefinition] =
    definitionsByNormalizedCode.get(normalize(code))

  def requireSupported(code: String): String =
    val normalized = normalize(code)
    definitionFor(normalized)
      .map(_.code)
      .getOrElse(
        throw IllegalArgumentException(
          s"Unsupported club privilege code '$code'. Supported codes: ${supportedCodes.mkString(", ")}"
        )
      )

  def supportedCodes: Vector[String] =
    definitions.map(_.code)
