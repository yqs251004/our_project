package riichinexus.domain.model

import java.time.Instant

enum AppealAttachmentStorageKind derives CanEqual:
  case ExternalUrl
  case ObjectStore
  case SignedUrl
  case InternalReference

enum AppealAttachmentMediaKind derives CanEqual:
  case Image
  case Video
  case Document
  case Log
  case Archive
  case Other

final case class AppealAttachment(
    name: String,
    uri: String,
    contentType: Option[String] = None,
    storageKind: AppealAttachmentStorageKind = AppealAttachmentStorageKind.ExternalUrl,
    mediaKind: AppealAttachmentMediaKind = AppealAttachmentMediaKind.Other,
    checksum: Option[String] = None,
    checksumAlgorithm: Option[String] = None,
    sizeBytes: Option[Long] = None,
    uploadedAt: Option[Instant] = None,
    retentionUntil: Option[Instant] = None
) derives CanEqual:
  require(name.trim.nonEmpty, "Appeal attachment name cannot be empty")
  require(uri.trim.nonEmpty, "Appeal attachment uri cannot be empty")
  require(sizeBytes.forall(_ > 0L), "Appeal attachment sizeBytes must be positive when provided")
  require(
    retentionUntil.forall(retention => uploadedAt.forall(!retention.isBefore(_))),
    "Appeal attachment retentionUntil cannot be earlier than uploadedAt"
  )

final case class AppealDecisionLog(
    operatorId: PlayerId,
    decision: String,
    decidedAt: Instant,
    note: Option[String] = None
) derives CanEqual

enum AppealPriority derives CanEqual:
  case Low
  case Normal
  case High
  case Critical

enum AppealStatus derives CanEqual:
  case Open
  case UnderReview
  case Resolved
  case Rejected
  case Escalated

enum AppealDecisionType derives CanEqual:
  case Resolve
  case Reject
  case Escalate

final case class AppealTicket(
    id: AppealTicketId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    openedBy: PlayerId,
    description: String,
    attachments: Vector[AppealAttachment] = Vector.empty,
    priority: AppealPriority = AppealPriority.Normal,
    assigneeId: Option[PlayerId] = None,
    dueAt: Option[Instant] = None,
    status: AppealStatus = AppealStatus.Open,
    logs: Vector[AppealDecisionLog] = Vector.empty,
    reopenCount: Int = 0,
    createdAt: Instant,
    updatedAt: Instant,
    resolution: Option[String] = None,
    version: Int = 0
) derives CanEqual:
  require(dueAt.forall(!_.isBefore(createdAt)), "Appeal dueAt cannot be earlier than createdAt")

  def assign(
      operatorId: PlayerId,
      assigneeId: Option[PlayerId],
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    copy(
      assigneeId = assigneeId,
      logs =
        logs :+ AppealDecisionLog(
          operatorId,
          assigneeId.map(assignee => s"assigned:${assignee.value}").getOrElse("unassigned"),
          at,
          note
        ),
      updatedAt = at
    )

  def reprioritize(
      operatorId: PlayerId,
      priority: AppealPriority,
      dueAt: Option[Instant],
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    copy(
      priority = priority,
      dueAt = dueAt,
      logs =
        logs :+ AppealDecisionLog(
          operatorId,
          s"triaged:${priority.toString.toLowerCase}",
          at,
          note.orElse(dueAt.map(value => s"dueAt=${value.toString}"))
        ),
      updatedAt = at
    )

  def markUnderReview(operatorId: PlayerId, at: Instant, note: Option[String] = None): AppealTicket =
    require(
      status == AppealStatus.Open || status == AppealStatus.Escalated,
      "Only open or escalated appeals can enter review"
    )
    copy(
      status = AppealStatus.UnderReview,
      logs = logs :+ AppealDecisionLog(operatorId, "under-review", at, note),
      updatedAt = at
    )

  def resolve(
      operatorId: PlayerId,
      verdict: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(verdict.trim.nonEmpty, "Appeal verdict cannot be empty")
    require(
      status == AppealStatus.Open || status == AppealStatus.UnderReview || status == AppealStatus.Escalated,
      "Only active appeals can be resolved"
    )
    copy(
      status = AppealStatus.Resolved,
      logs = logs :+ AppealDecisionLog(operatorId, verdict, at, note),
      updatedAt = at,
      resolution = Some(verdict)
    )

  def reject(
      operatorId: PlayerId,
      verdict: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(verdict.trim.nonEmpty, "Appeal rejection reason cannot be empty")
    require(
      status == AppealStatus.Open || status == AppealStatus.UnderReview || status == AppealStatus.Escalated,
      "Only active appeals can be rejected"
    )
    copy(
      status = AppealStatus.Rejected,
      logs = logs :+ AppealDecisionLog(operatorId, s"rejected:$verdict", at, note),
      updatedAt = at,
      resolution = Some(verdict)
    )

  def escalate(
      operatorId: PlayerId,
      reason: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(reason.trim.nonEmpty, "Appeal escalation reason cannot be empty")
    require(
      status == AppealStatus.Open || status == AppealStatus.UnderReview,
      "Only open or under-review appeals can be escalated"
    )
    copy(
      status = AppealStatus.Escalated,
      logs = logs :+ AppealDecisionLog(operatorId, s"escalated:$reason", at, note),
      updatedAt = at,
      resolution = Some(reason)
    )

  def reopen(
      operatorId: PlayerId,
      reason: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(reason.trim.nonEmpty, "Appeal reopen reason cannot be empty")
    require(
      status == AppealStatus.Resolved || status == AppealStatus.Rejected,
      "Only resolved or rejected appeals can be reopened"
    )
    copy(
      status = AppealStatus.Open,
      logs = logs :+ AppealDecisionLog(operatorId, s"reopened:$reason", at, note),
      reopenCount = reopenCount + 1,
      updatedAt = at,
      resolution = None
    )
