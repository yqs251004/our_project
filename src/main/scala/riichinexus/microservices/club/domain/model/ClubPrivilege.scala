package riichinexus.microservices.club.domain.model

object ClubPrivilege:
  val PriorityLineup: String = ClubPrivilegeRegistry.requireSupported("priority-lineup")
  val ApproveRoster: String = ClubPrivilegeRegistry.requireSupported("approve-roster")
  val ManageBank: String = ClubPrivilegeRegistry.requireSupported("manage-bank")

  def normalize(value: String): String =
    ClubPrivilegeRegistry.normalize(value)
