package riichinexus.domain.event

import java.time.Instant

import riichinexus.domain.model.*

sealed trait DomainEvent:
  def occurredAt: Instant

final case class TableResultRecorded(
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    paifu: Paifu,
    occurredAt: Instant
) extends DomainEvent
