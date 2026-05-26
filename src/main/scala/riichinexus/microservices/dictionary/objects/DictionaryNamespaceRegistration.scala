package riichinexus.microservices.dictionary.objects

import java.time.{Duration, Instant}

import riichinexus.domain.model.{ClubId, PlayerId}

final case class DictionaryNamespaceRegistration(
    namespacePrefix: String,
    contextClubId: Option[ClubId] = None,
    ownerPlayerId: PlayerId,
    coOwnerPlayerIds: Vector[PlayerId] = Vector.empty,
    editorPlayerIds: Vector[PlayerId] = Vector.empty,
    requestedBy: PlayerId,
    requestedAt: Instant,
    reviewDueAt: Option[Instant] = None,
    lastReminderAt: Option[Instant] = None,
    reminderCount: Int = 0,
    status: DictionaryNamespaceReviewStatus = DictionaryNamespaceReviewStatus.Pending,
    reviewedBy: Option[PlayerId] = None,
    reviewedAt: Option[Instant] = None,
    reviewNote: Option[String] = None,
    version: Int = 0
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

  def isPendingOverdue(asOf: Instant): Boolean =
    status == DictionaryNamespaceReviewStatus.Pending && reviewDueAt.exists(_.isBefore(asOf))

  def isPendingDueSoon(
      asOf: Instant,
      dueSoonWindow: Duration = Duration.ofHours(24)
  ): Boolean =
    status == DictionaryNamespaceReviewStatus.Pending &&
      reviewDueAt.exists { dueAt =>
        !dueAt.isBefore(asOf) && !dueAt.isAfter(asOf.plus(dueSoonWindow))
      }

  def approve(by: PlayerId, at: Instant, note: Option[String] = None): DictionaryNamespaceRegistration =
    require(status == DictionaryNamespaceReviewStatus.Pending, "Only pending namespace requests can be approved")
    copy(
      status = DictionaryNamespaceReviewStatus.Approved,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def reject(by: PlayerId, at: Instant, note: Option[String] = None): DictionaryNamespaceRegistration =
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
      at: Instant,
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
      at: Instant,
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
      at: Instant,
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

  def markReminderSent(at: Instant): DictionaryNamespaceRegistration =
    copy(
      lastReminderAt = Some(at),
      reminderCount = reminderCount + 1
    )

  def revoke(by: PlayerId, at: Instant, note: Option[String] = None): DictionaryNamespaceRegistration =
    require(status == DictionaryNamespaceReviewStatus.Approved, "Only approved namespace registrations can be revoked")
    copy(
      status = DictionaryNamespaceReviewStatus.Revoked,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )
