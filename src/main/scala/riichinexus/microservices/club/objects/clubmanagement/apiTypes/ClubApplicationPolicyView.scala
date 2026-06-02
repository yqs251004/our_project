package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.*
import riichinexus.system.json.JsonCodecs.given

final case class ClubApplicationPolicyView(
    applicationsOpen: Boolean,
    requirementsText: Option[String],
    expectedReviewSlaHours: Option[Int],
    pendingApplicationCount: Int
)

object ClubApplicationPolicyView:
  given ReadWriter[ClubApplicationPolicyView] = macroRW
