package riichinexus.domain.event

import java.time.Instant

import riichinexus.domain.model.*

sealed trait DomainEvent:
  def occurredAt: Instant

final case class MatchRecordArchived(
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    matchRecord: MatchRecord,
    paifu: Option[Paifu],
    occurredAt: Instant
) extends DomainEvent

final case class AppealTicketFiled(
    ticket: AppealTicket,
    occurredAt: Instant
) extends DomainEvent

final case class AppealTicketResolved(
    ticket: AppealTicket,
    occurredAt: Instant
) extends DomainEvent

final case class GlobalDictionaryUpdated(
    entry: GlobalDictionaryEntry,
    occurredAt: Instant
) extends DomainEvent

final case class PlayerBanned(
    playerId: PlayerId,
    reason: String,
    occurredAt: Instant
) extends DomainEvent

final case class ClubDissolved(
    clubId: ClubId,
    occurredAt: Instant
) extends DomainEvent
