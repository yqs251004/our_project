package riichinexus.microservices.club.domain.model

final case class ClubRecruitmentPolicy(
    applicationsOpen: Boolean = true,
    requirementsText: Option[String] = Some("Open to guest or registered applicants; final approval is handled manually by club admins."),
    expectedReviewSlaHours: Option[Int] = Some(72)
) derives CanEqual:
  requirementsText.foreach(text =>
    require(text.trim.nonEmpty, "Club recruitment requirements text cannot be empty")
  )
  expectedReviewSlaHours.foreach(hours =>
    require(hours > 0, "Club recruitment expected review SLA must be positive")
  )
