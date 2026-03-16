package riichinexus.domain.model

enum GlobalDictionaryValueType derives CanEqual:
  case Integer
  case Decimal
  case Weight
  case RatioVector
  case StageRuleTemplate
  case Metadata

final case class GlobalDictionarySchemaEntry(
    id: String,
    keyPattern: String,
    valueType: GlobalDictionaryValueType,
    description: String,
    validationHint: String,
    runtimeConsumers: Vector[String],
    examples: Vector[String]
) derives CanEqual

final case class GlobalDictionarySchemaView(
    entries: Vector[GlobalDictionarySchemaEntry],
    unknownKeyPolicy: String
) derives CanEqual

enum DictionaryNamespaceReviewStatus derives CanEqual:
  case Pending
  case Approved
  case Rejected
  case Revoked

final case class DictionaryNamespaceRegistration(
    namespacePrefix: String,
    contextClubId: Option[ClubId] = None,
    ownerPlayerId: PlayerId,
    coOwnerPlayerIds: Vector[PlayerId] = Vector.empty,
    editorPlayerIds: Vector[PlayerId] = Vector.empty,
    requestedBy: PlayerId,
    requestedAt: java.time.Instant,
    reviewDueAt: Option[java.time.Instant] = None,
    lastReminderAt: Option[java.time.Instant] = None,
    reminderCount: Int = 0,
    status: DictionaryNamespaceReviewStatus = DictionaryNamespaceReviewStatus.Pending,
    reviewedBy: Option[PlayerId] = None,
    reviewedAt: Option[java.time.Instant] = None,
    reviewNote: Option[String] = None
) derives CanEqual:
  require(namespacePrefix.trim.nonEmpty, "Dictionary namespace prefix cannot be empty")
  require(reminderCount >= 0, "Dictionary namespace reminderCount cannot be negative")

  def hasContextClub(clubId: ClubId): Boolean =
    contextClubId.contains(clubId)

  def ownerIds: Vector[PlayerId] =
    (ownerPlayerId +: coOwnerPlayerIds).distinct

  def writerIds: Vector[PlayerId] =
    (ownerIds ++ editorPlayerIds).distinct

  def hasOwnership(playerId: PlayerId): Boolean =
    ownerIds.contains(playerId)

  def hasWriteAccess(playerId: PlayerId): Boolean =
    writerIds.contains(playerId)

  def isPendingOverdue(asOf: java.time.Instant): Boolean =
    status == DictionaryNamespaceReviewStatus.Pending && reviewDueAt.exists(_.isBefore(asOf))

  def isPendingDueSoon(
      asOf: java.time.Instant,
      dueSoonWindow: java.time.Duration = java.time.Duration.ofHours(24)
  ): Boolean =
    status == DictionaryNamespaceReviewStatus.Pending &&
      reviewDueAt.exists { dueAt =>
        !dueAt.isBefore(asOf) && !dueAt.isAfter(asOf.plus(dueSoonWindow))
      }

  def approve(by: PlayerId, at: java.time.Instant, note: Option[String] = None): DictionaryNamespaceRegistration =
    require(status == DictionaryNamespaceReviewStatus.Pending, "Only pending namespace requests can be approved")
    copy(
      status = DictionaryNamespaceReviewStatus.Approved,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def reject(by: PlayerId, at: java.time.Instant, note: Option[String] = None): DictionaryNamespaceRegistration =
    require(status == DictionaryNamespaceReviewStatus.Pending, "Only pending namespace requests can be rejected")
    copy(
      status = DictionaryNamespaceReviewStatus.Rejected,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def updateCollaborators(
      coOwners: Vector[PlayerId],
      editors: Vector[PlayerId],
      by: PlayerId,
      at: java.time.Instant,
      note: Option[String] = None
  ): DictionaryNamespaceRegistration =
    require(status == DictionaryNamespaceReviewStatus.Approved, "Only approved namespace registrations can update collaborators")
    val normalizedCoOwners = coOwners.distinct.filterNot(_ == ownerPlayerId)
    val normalizedEditors = editors.distinct.filterNot(playerId => playerId == ownerPlayerId || normalizedCoOwners.contains(playerId))
    copy(
      coOwnerPlayerIds = normalizedCoOwners,
      editorPlayerIds = normalizedEditors,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def updateContextClub(
      clubId: Option[ClubId],
      by: PlayerId,
      at: java.time.Instant,
      note: Option[String] = None
  ): DictionaryNamespaceRegistration =
    require(status == DictionaryNamespaceReviewStatus.Approved, "Only approved namespace registrations can update context club")
    copy(
      contextClubId = clubId,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def transferOwnership(
      newOwner: PlayerId,
      by: PlayerId,
      at: java.time.Instant,
      note: Option[String] = None
  ): DictionaryNamespaceRegistration =
    require(status == DictionaryNamespaceReviewStatus.Approved, "Only approved namespace registrations can be transferred")
    require(newOwner != ownerPlayerId, "Dictionary namespace is already owned by the requested player")
    val normalizedCoOwners = (ownerPlayerId +: coOwnerPlayerIds.filterNot(_ == newOwner)).distinct
    copy(
      ownerPlayerId = newOwner,
      coOwnerPlayerIds = normalizedCoOwners.filterNot(_ == newOwner),
      editorPlayerIds = editorPlayerIds.filterNot(_ == newOwner),
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def markReminderSent(at: java.time.Instant): DictionaryNamespaceRegistration =
    copy(
      lastReminderAt = Some(at),
      reminderCount = reminderCount + 1
    )

  def revoke(by: PlayerId, at: java.time.Instant, note: Option[String] = None): DictionaryNamespaceRegistration =
    require(status == DictionaryNamespaceReviewStatus.Approved, "Only approved namespace registrations can be revoked")
    copy(
      status = DictionaryNamespaceReviewStatus.Revoked,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

final case class DictionaryNamespaceOwnerBacklog(
    ownerPlayerId: PlayerId,
    pendingCount: Int,
    overdueCount: Int,
    dueSoonCount: Int
) derives CanEqual

final case class DictionaryNamespaceBacklogView(
    asOf: java.time.Instant,
    pendingCount: Int,
    overdueCount: Int,
    dueSoonCount: Int,
    oldestPendingRequestedAt: Option[java.time.Instant],
    nextDueAt: Option[java.time.Instant],
    ownerBacklog: Vector[DictionaryNamespaceOwnerBacklog]
) derives CanEqual

enum DictionaryNamespaceReminderKind derives CanEqual:
  case DueSoon
  case Overdue
  case Escalated

final case class DictionaryNamespaceReminderAction(
    namespacePrefix: String,
    contextClubId: Option[ClubId],
    ownerPlayerId: PlayerId,
    coOwnerPlayerIds: Vector[PlayerId],
    editorPlayerIds: Vector[PlayerId],
    reminderKind: DictionaryNamespaceReminderKind,
    triggeredAt: java.time.Instant,
    dueAt: Option[java.time.Instant],
    reminderCount: Int
) derives CanEqual
