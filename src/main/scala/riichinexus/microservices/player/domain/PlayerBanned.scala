package riichinexus.microservices.player.domain

import java.time.Instant

import riichinexus.application.ports.DomainEvent
import riichinexus.domain.model.PlayerId

final case class PlayerBanned(
    playerId: PlayerId,
    reason: String,
    occurredAt: Instant
) extends DomainEvent
