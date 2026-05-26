package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.domain.model.ClubRecruitmentPolicy
import upickle.default.*

final case class UpdateClubRecruitmentPolicyRequest(
    operatorId: String,
    applicationsOpen: Boolean,
    requirementsText: Option[String] = None,
    expectedReviewSlaHours: Option[Int] = None,
    note: Option[String] = None
):
  expectedReviewSlaHours.foreach(hours =>
    require(hours > 0, "Recruitment policy expectedReviewSlaHours must be positive")
  )

  def operator: PlayerId =
    PlayerId(operatorId)

  def policy: ClubRecruitmentPolicy =
    ClubRecruitmentPolicy(
      applicationsOpen = applicationsOpen,
      requirementsText = requirementsText.map(_.trim).filter(_.nonEmpty),
      expectedReviewSlaHours = expectedReviewSlaHours
    )

object UpdateClubRecruitmentPolicyRequest:
  given ReadWriter[UpdateClubRecruitmentPolicyRequest] = macroRW
