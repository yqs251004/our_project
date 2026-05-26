package riichinexus.microservices.club.domain.model

import java.time.Instant

final case class ClubHonor(
    title: String,
    achievedAt: Instant,
    note: Option[String] = None
) derives CanEqual:
  require(title.trim.nonEmpty, "Club honor title cannot be empty")
