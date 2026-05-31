package riichinexus.microservices.club.domain.model

import riichinexus.microservices.club.objects.ClubPrivilegeCode

object ClubPrivilege:
  val PriorityLineup: ClubPrivilegeCode = ClubPrivilegeCode.PriorityLineup
  val ApproveRoster: ClubPrivilegeCode = ClubPrivilegeCode.ApproveRoster
  val ManageBank: ClubPrivilegeCode = ClubPrivilegeCode.ManageBank

  def normalize(value: String): ClubPrivilegeCode =
    ClubPrivilegeRegistry.requireSupported(value)
