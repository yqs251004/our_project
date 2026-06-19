package riichinexus.microservices.club.domain.rankprivilegemanagement.functions

import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.club.objects.rankprivilegemanagement.{ClubPrivilegeCode, ClubPrivilegeDefinition}

/** ClubPrivilegeRegistry 提供俱乐部权限注册表 相关的领域计算、校验和转换函数。 */

private[club] object ClubPrivilegeRegistry:
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
    definitions.map(definition => normalize(ClubPrivilegeCode.toString(definition.code)) -> definition).toMap

  def normalize(value: String): String =
    value.trim.toLowerCase

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
    definitions.map(definition => ClubPrivilegeCode.toString(definition.code))
