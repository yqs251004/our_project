package riichinexus.microservices.club.domain.clubmanagement.model

import java.time.Instant

import riichinexus.application.ports.DomainEvent
import riichinexus.domain.model.ClubId

final case class ClubDissolved(
    clubId: ClubId,
    occurredAt: Instant
) extends DomainEvent
