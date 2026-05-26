package riichinexus.domain.model

final case class PlayerId(value: String) derives CanEqual
final case class ClubId(value: String) derives CanEqual
final case class TournamentId(value: String) derives CanEqual
final case class TournamentStageId(value: String) derives CanEqual
final case class TableId(value: String) derives CanEqual
final case class PaifuId(value: String) derives CanEqual
final case class MatchRecordId(value: String) derives CanEqual
final case class AppealTicketId(value: String) derives CanEqual
final case class MembershipApplicationId(value: String) derives CanEqual
final case class LineupSubmissionId(value: String) derives CanEqual
final case class GuestSessionId(value: String) derives CanEqual
final case class SettlementSnapshotId(value: String) derives CanEqual
final case class AuditEventId(value: String) derives CanEqual
final case class AdvancedStatsRecomputeTaskId(value: String) derives CanEqual
final case class EventCascadeRecordId(value: String) derives CanEqual
final case class DomainEventOutboxRecordId(value: String) derives CanEqual
final case class DomainEventDeliveryReceiptId(value: String) derives CanEqual
final case class DomainEventSubscriberCursorId(value: String) derives CanEqual
