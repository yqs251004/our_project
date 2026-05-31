package riichinexus.microservices.tournament.appeal.domain

import java.time.Instant

import riichinexus.application.ports.DomainEvent
import riichinexus.microservices.tournament.appeal.domain.model.*

final case class AppealTicketAdjudicated(
    ticket: AppealTicket,
    decision: AppealDecisionType,
    tableResolution: Option[AppealTableResolution],
    occurredAt: Instant
) extends DomainEvent
