package riichinexus.microservices.club.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.ClubApplicationStatus
import upickle.default.*

final case class ClubApplicationListQuery(
    operatorId: String,
    status: Option[ClubApplicationStatus] = None,
    applicantUserId: Option[String] = None,
    displayName: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubApplicationListQuery:
  given ReadWriter[ClubApplicationListQuery] = macroRW
