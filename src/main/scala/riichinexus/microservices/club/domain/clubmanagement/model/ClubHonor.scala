package riichinexus.microservices.club.domain.clubmanagement.model

import java.time.Instant

final case class ClubHonor(
    title: String,
    achievedAt: Instant,
    note: Option[String] = None
) derives CanEqual
