package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class ClubMemberListQuery(
    status: Option[String] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubMemberListQuery:
  given ReadWriter[ClubMemberListQuery] = macroRW
