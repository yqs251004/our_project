package riichinexus.microservices.publicquery.objects.apiTypes

import upickle.default.*

final case class ClubApplicationPolicyView(
    applicationsOpen: Boolean,
    requirementsText: Option[String],
    expectedReviewSlaHours: Option[Int],
    pendingApplicationCount: Int
) derives CanEqual

object ClubApplicationPolicyView:
  given ReadWriter[ClubApplicationPolicyView] = macroRW
