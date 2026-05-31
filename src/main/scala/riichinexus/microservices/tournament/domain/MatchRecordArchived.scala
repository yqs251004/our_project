package riichinexus.microservices.tournament.domain

import java.time.Instant

import riichinexus.application.ports.DomainEvent
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*

final case class MatchRecordArchived(
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    matchRecord: MatchRecord,
    paifu: Option[Paifu],
    occurredAt: Instant
) extends DomainEvent
