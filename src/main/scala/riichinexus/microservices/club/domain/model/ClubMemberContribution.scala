package riichinexus.microservices.club.domain.model

import java.time.Instant

import riichinexus.domain.model.PlayerId

final case class ClubMemberContribution(
    playerId: PlayerId,
    amount: Int,
    updatedAt: Instant,
    updatedBy: PlayerId,
    note: Option[String] = None
) derives CanEqual:
  require(amount >= 0, "Club member contribution cannot be negative")
