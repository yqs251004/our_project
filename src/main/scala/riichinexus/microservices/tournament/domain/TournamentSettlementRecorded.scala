package riichinexus.microservices.tournament.domain

import java.time.Instant

import riichinexus.application.ports.DomainEvent
import riichinexus.microservices.tournament.domain.model.TournamentSettlementSnapshot

final case class TournamentSettlementRecorded(
    settlement: TournamentSettlementSnapshot,
    occurredAt: Instant
) extends DomainEvent
