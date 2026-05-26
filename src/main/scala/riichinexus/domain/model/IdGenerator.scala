package riichinexus.domain.model

import java.util.UUID

object IdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def playerId(): PlayerId = PlayerId(nextId("player"))
  def clubId(): ClubId = ClubId(nextId("club"))
  def tournamentId(): TournamentId = TournamentId(nextId("tournament"))
  def stageId(): TournamentStageId = TournamentStageId(nextId("stage"))
  def tableId(): TableId = TableId(nextId("table"))
  def paifuId(): PaifuId = PaifuId(nextId("paifu"))
  def matchRecordId(): MatchRecordId = MatchRecordId(nextId("record"))
  def appealTicketId(): AppealTicketId = AppealTicketId(nextId("appeal"))
  def membershipApplicationId(): MembershipApplicationId =
    MembershipApplicationId(nextId("membership"))
  def lineupSubmissionId(): LineupSubmissionId = LineupSubmissionId(nextId("lineup"))
  def guestSessionId(): GuestSessionId = GuestSessionId(nextId("guest"))
  def settlementSnapshotId(): SettlementSnapshotId = SettlementSnapshotId(nextId("settlement"))
  def auditEventId(): AuditEventId = AuditEventId(nextId("audit"))
  def advancedStatsRecomputeTaskId(): AdvancedStatsRecomputeTaskId =
    AdvancedStatsRecomputeTaskId(nextId("advanced-stats-task"))
  def eventCascadeRecordId(): EventCascadeRecordId =
    EventCascadeRecordId(nextId("event-cascade"))
  def domainEventOutboxRecordId(): DomainEventOutboxRecordId =
    DomainEventOutboxRecordId(nextId("event-outbox"))
  def domainEventDeliveryReceiptId(): DomainEventDeliveryReceiptId =
    DomainEventDeliveryReceiptId(nextId("event-delivery"))
  def domainEventSubscriberCursorId(): DomainEventSubscriberCursorId =
    DomainEventSubscriberCursorId(nextId("event-cursor"))
