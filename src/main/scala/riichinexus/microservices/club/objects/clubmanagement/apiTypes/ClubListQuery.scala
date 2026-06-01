package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.*

final case class ClubListQuery(
    activeOnly: Option[Boolean] = None,
    joinableOnly: Option[Boolean] = None,
    memberId: Option[String] = None,
    adminId: Option[String] = None,
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubListQuery:
  given ReadWriter[ClubListQuery] = macroRW
