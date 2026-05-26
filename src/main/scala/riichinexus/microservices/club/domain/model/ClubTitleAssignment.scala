package riichinexus.microservices.club.domain.model

import java.time.Instant

import riichinexus.domain.model.PlayerId

final case class ClubTitleAssignment(
    playerId: PlayerId,
    title: String,
    assignedBy: PlayerId,
    assignedAt: Instant,
    note: Option[String] = None
) derives CanEqual
