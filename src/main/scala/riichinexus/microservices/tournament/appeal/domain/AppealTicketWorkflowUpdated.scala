package riichinexus.microservices.tournament.appeal.domain

import java.time.Instant

import riichinexus.application.ports.DomainEvent
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket

final case class AppealTicketWorkflowUpdated(
    ticket: AppealTicket,
    occurredAt: Instant
) extends DomainEvent
