package riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes

import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class ClubMemberPrivilegeListQuery(
    playerId: Option[String] = None,
    privilege: Option[ClubPrivilegeCode] = None,
    rankCode: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubMemberPrivilegeListQuery:
  given ReadWriter[ClubMemberPrivilegeListQuery] = macroRW
