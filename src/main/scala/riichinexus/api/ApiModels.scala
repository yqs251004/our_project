package riichinexus.api

import java.time.Instant

import json.JsonCodecs.given
import riichinexus.domain.model.*
import upickle.default.*

final case class ApiError(
    message: String,
    code: String = "internal_error",
    details: Map[String, String] = Map.empty
)

final case class ApiMessage(
    message: String
)

final case class HealthResponse(
    status: String,
    storage: String,
    timestamp: Instant
)

final case class PagedResponse[T](
    items: Vector[T],
    total: Int,
    limit: Int,
    offset: Int,
    hasMore: Boolean,
    appliedFilters: Map[String, String] = Map.empty
)

final case class CreatePlayerRequest(
    userId: String,
    nickname: String,
    rankPlatform: String,
    tier: String,
    stars: Option[Int] = None,
    initialElo: Int = 1500
):
  def toRankSnapshot: RankSnapshot =
    RankSnapshot(RankPlatform.valueOf(rankPlatform), tier, stars)

final case class RegisterAccountRequest(
    username: String,
    password: String,
    displayName: String
)

final case class LoginRequest(
    username: String,
    password: String
)

final case class CreateClubRequest(
    name: String,
    creatorId: String
):
  def creator: PlayerId =
    PlayerId(creatorId)

final case class CreateGuestSessionRequest(
    displayName: Option[String] = None,
    ttlHours: Option[Int] = None,
    deviceFingerprint: Option[String] = None
):
  ttlHours.foreach(hours => require(hours > 0, "Guest session ttlHours must be positive"))

final case class RevokeGuestSessionRequest(
    reason: Option[String] = None
)

final case class UpgradeGuestSessionRequest(
    playerId: String
):
  def player: PlayerId =
    PlayerId(playerId)

final case class AddClubMemberRequest(
    playerId: String,
    operatorId: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

final case class ClubMembershipApplicationRequest(
    applicantUserId: Option[String],
    displayName: String,
    message: Option[String] = None,
    guestSessionId: Option[String] = None,
    operatorId: Option[String] = None
):
  def session: Option[GuestSessionId] =
    guestSessionId.map(GuestSessionId(_))

  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

final case class ApproveClubApplicationRequest(
    playerId: String,
    operatorId: String,
    note: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: PlayerId =
    PlayerId(operatorId)

final case class AssignClubAdminRequest(
    playerId: String,
    operatorId: String
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: PlayerId =
    PlayerId(operatorId)

final case class AssignClubTitleRequest(
    playerId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: PlayerId =
    PlayerId(operatorId)

final case class ClearClubTitleRequest(
    operatorId: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class AdjustClubTreasuryRequest(
    operatorId: String,
    delta: Long,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class AdjustClubPointPoolRequest(
    operatorId: String,
    delta: Int,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class AdjustClubMemberContributionRequest(
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def player: PlayerId =
    PlayerId(playerId)

final case class ClubRankNodeRequest(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[String] = Vector.empty
):
  def toNode: ClubRankNode =
    ClubRankNode(
      code = code,
      label = label,
      minimumContribution = minimumContribution,
      privileges = privileges
    )

final case class UpdateClubRankTreeRequest(
    operatorId: String,
    ranks: Vector[ClubRankNodeRequest],
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def nodes: Vector[ClubRankNode] =
    ranks.map(_.toNode)

final case class AwardClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None,
    achievedAt: Option[Instant] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def honor: ClubHonor =
    ClubHonor(
      title = title,
      achievedAt = achievedAt.getOrElse(Instant.now()),
      note = note
    )

final case class RevokeClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class UpdateClubRecruitmentPolicyRequest(
    operatorId: String,
    applicationsOpen: Boolean,
    requirementsText: Option[String] = None,
    expectedReviewSlaHours: Option[Int] = None,
    note: Option[String] = None
):
  expectedReviewSlaHours.foreach(hours =>
    require(hours > 0, "Recruitment policy expectedReviewSlaHours must be positive")
  )

  def operator: PlayerId =
    PlayerId(operatorId)

  def policy: ClubRecruitmentPolicy =
    ClubRecruitmentPolicy(
      applicationsOpen = applicationsOpen,
      requirementsText = requirementsText.map(_.trim).filter(_.nonEmpty),
      expectedReviewSlaHours = expectedReviewSlaHours
    )

final case class CreateTournamentStageRequest(
    id: Option[String],
    name: String,
    format: String,
    order: Int,
    roundCount: Int,
    operatorId: Option[String] = None,
    ruleTemplateKey: Option[String] = None,
    advancementRuleType: Option[String] = None,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    schedulingPoolSize: Option[Int] = None
):
  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

  def toStage: TournamentStage =
    val stageFormat = StageFormat.valueOf(format)
    TournamentStage(
      id = id.map(TournamentStageId(_)).getOrElse(IdGenerator.stageId()),
      name = name,
      format = stageFormat,
      order = order,
      roundCount = roundCount,
      advancementRule = advancementRuleType
        .map(rule =>
          AdvancementRule(
            ruleType = AdvancementRuleType.valueOf(rule),
            cutSize = cutSize,
            thresholdScore = thresholdScore,
            targetTableCount = targetTableCount,
            templateKey = ruleTemplateKey
          )
        )
        .getOrElse(
          AdvancementRule.defaultFor(stageFormat).copy(
            templateKey = ruleTemplateKey
          )
        ),
      schedulingPoolSize = schedulingPoolSize.getOrElse(4)
    )

final case class CreateTournamentRequest(
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    stages: Vector[CreateTournamentStageRequest],
    adminId: Option[String] = None
):
  def toStages: Vector[TournamentStage] =
    stages.map(_.toStage)

  def admin: Option[PlayerId] =
    adminId.map(PlayerId(_))

final case class ConfigureStageRulesRequest(
    operatorId: String,
    advancementRuleType: Option[String] = None,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    schedulingPoolSize: Option[Int] = None,
    ruleTemplateKey: Option[String] = None,
    pairingMethod: Option[String] = None,
    carryOverPoints: Option[Boolean] = None,
    maxRounds: Option[Int] = None,
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Option[Boolean] = None,
    repechageEnabled: Option[Boolean] = None,
    seedingPolicy: Option[String] = None,
    note: Option[String] = None
):
  require(
    advancementRuleType.nonEmpty || ruleTemplateKey.nonEmpty,
    "ConfigureStageRulesRequest requires advancementRuleType or ruleTemplateKey"
  )

  def operator: PlayerId =
    PlayerId(operatorId)

  def advancementRule: AdvancementRule =
    AdvancementRule(
      ruleType = advancementRuleType.map(AdvancementRuleType.valueOf).getOrElse(AdvancementRuleType.Custom),
      cutSize = cutSize,
      thresholdScore = thresholdScore,
      targetTableCount = targetTableCount,
      templateKey = ruleTemplateKey,
      note = note
    )

  def swissRule: Option[SwissRuleConfig] =
    if pairingMethod.isDefined || carryOverPoints.isDefined || maxRounds.isDefined then
      Some(
        SwissRuleConfig(
          pairingMethod = pairingMethod.map(_.trim.toLowerCase).getOrElse("balanced-elo"),
          carryOverPoints = carryOverPoints.getOrElse(true),
          maxRounds = maxRounds
        )
      )
    else None

  def knockoutRule: Option[KnockoutRuleConfig] =
    if bracketSize.isDefined || thirdPlaceMatch.isDefined || seedingPolicy.isDefined || repechageEnabled.isDefined then
      Some(
        KnockoutRuleConfig(
          bracketSize = bracketSize,
          thirdPlaceMatch = thirdPlaceMatch.getOrElse(false),
          seedingPolicy = seedingPolicy.map(_.trim.toLowerCase).getOrElse("rating"),
          repechageEnabled = repechageEnabled.getOrElse(false)
        )
      )
    else None

final case class StageLineupSeatRequest(
    playerId: String,
    preferredWind: Option[String] = None,
    reserve: Boolean = false
):
  def toSeat: StageLineupSeat =
    StageLineupSeat(
      playerId = PlayerId(playerId),
      preferredWind = preferredWind.map(SeatWind.valueOf),
      reserve = reserve
    )

final case class SubmitStageLineupRequest(
    clubId: String,
    operatorId: String,
    seats: Vector[StageLineupSeatRequest],
    note: Option[String] = None
):
  def toSubmission: StageLineupSubmission =
    StageLineupSubmission(
      id = IdGenerator.lineupSubmissionId(),
      clubId = ClubId(clubId),
      submittedBy = PlayerId(operatorId),
      submittedAt = Instant.now(),
      seats = seats.map(_.toSeat),
      note = note
    )

  def operator: PlayerId =
    PlayerId(operatorId)

final case class AssignTournamentAdminRequest(
    playerId: String,
    operatorId: String
):
  def player: PlayerId =
    PlayerId(playerId)

  def operator: PlayerId =
    PlayerId(operatorId)

final case class OperatorRequest(
    operatorId: Option[String] = None
):
  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

final case class UpdateOwnTableReadyStateRequest(
    operatorId: String,
    ready: Boolean = true,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class RejectClubApplicationRequest(
    operatorId: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class ReviewClubApplicationRequest(
    operatorId: String,
    decision: String,
    playerId: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def player: Option[PlayerId] =
    playerId.map(PlayerId(_))

final case class WithdrawClubApplicationRequest(
    guestSessionId: Option[String] = None,
    operatorId: Option[String] = None,
    note: Option[String] = None
):
  def session: Option[GuestSessionId] =
    guestSessionId.map(GuestSessionId(_))

  def operator: Option[PlayerId] =
    operatorId.map(PlayerId(_))

final case class UpdateClubRelationRequest(
    operatorId: String,
    targetClubId: String,
    relation: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def toRelation(updatedAt: Instant = Instant.now()): ClubRelation =
    ClubRelation(
      targetClubId = ClubId(targetClubId),
      relation = ClubRelationKind.valueOf(relation),
      updatedAt = updatedAt,
      note = note
    )

final case class AppealAttachmentRequest(
    name: String,
    uri: String,
    contentType: Option[String] = None,
    storageKind: Option[String] = None,
    mediaKind: Option[String] = None,
    checksum: Option[String] = None,
    checksumAlgorithm: Option[String] = None,
    sizeBytes: Option[Long] = None,
    uploadedAt: Option[Instant] = None,
    retentionUntil: Option[Instant] = None
):
  def toAttachment: AppealAttachment =
    AppealAttachment(
      name = name,
      uri = uri,
      contentType = contentType,
      storageKind = storageKind.map(AppealAttachmentStorageKind.valueOf).getOrElse(AppealAttachmentStorageKind.ExternalUrl),
      mediaKind = mediaKind.map(AppealAttachmentMediaKind.valueOf).getOrElse(AppealAttachmentMediaKind.Other),
      checksum = checksum,
      checksumAlgorithm = checksumAlgorithm,
      sizeBytes = sizeBytes,
      uploadedAt = uploadedAt,
      retentionUntil = retentionUntil
    )

final case class FileAppealRequest(
    playerId: String,
    description: String,
    attachments: Vector[AppealAttachmentRequest] = Vector.empty,
    priority: Option[String] = None,
    dueAt: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def priorityLevel: AppealPriority =
    priority.map(AppealPriority.valueOf).getOrElse(AppealPriority.Normal)

  def dueAtInstant: Option[Instant] =
    dueAt.map(Instant.parse)

final case class ResolveAppealRequest(
    operatorId: String,
    verdict: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class AdjudicateAppealRequest(
    operatorId: String,
    decision: String,
    verdict: String,
    tableResolution: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def decisionType: AppealDecisionType =
    AppealDecisionType.valueOf(decision)

  def resolution: Option[AppealTableResolution] =
    tableResolution.map(AppealTableResolution.valueOf)

final case class UpdateAppealWorkflowRequest(
    operatorId: String,
    assigneeId: Option[String] = None,
    clearAssignee: Boolean = false,
    priority: Option[String] = None,
    dueAt: Option[String] = None,
    clearDueAt: Boolean = false,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def assignee: Option[PlayerId] =
    assigneeId.map(PlayerId(_))

  def priorityLevel: Option[AppealPriority] =
    priority.map(AppealPriority.valueOf)

  def dueAtInstant: Option[Instant] =
    dueAt.map(Instant.parse)

final case class ReopenAppealRequest(
    operatorId: String,
    reason: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class UploadPaifuRequest(
    operatorId: String,
    paifu: Paifu
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class CompleteStageRequest(
    operatorId: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class AdvanceKnockoutStageRequest(
    operatorId: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class SettleTournamentRequest(
    operatorId: String,
    finalStageId: String,
    prizePool: Long = 0L,
    payoutRatios: Vector[Double] = Vector.empty,
    houseFeeAmount: Long = 0L,
    clubShareRatio: Double = 0.0,
    adjustments: Vector[SettlementAdjustmentRequest] = Vector.empty,
    finalizeSettlement: Boolean = true,
    note: Option[String] = None
):
  require(houseFeeAmount >= 0L, "Tournament settlement houseFeeAmount must be non-negative")
  require(clubShareRatio >= 0.0 && clubShareRatio <= 1.0, "Tournament settlement clubShareRatio must be between 0.0 and 1.0")

  def operator: PlayerId =
    PlayerId(operatorId)

  def stageId: TournamentStageId =
    TournamentStageId(finalStageId)

final case class SettlementAdjustmentRequest(
    playerId: String,
    label: String,
    amount: Long,
    note: Option[String] = None
):
  def adjustment: TournamentSettlementAdjustment =
    TournamentSettlementAdjustment(
      playerId = PlayerId(playerId),
      label = label,
      amount = amount,
      note = note
    )

final case class FinalizeTournamentSettlementRequest(
    operatorId: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class ForceResetTableRequest(
    operatorId: String,
    note: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class UpdateTableSeatStateRequest(
    operatorId: String,
    ready: Option[Boolean] = None,
    disconnected: Option[Boolean] = None,
    note: Option[String] = None
):
  require(ready.isDefined || disconnected.isDefined, "Seat state update must modify at least one flag")

  def operator: PlayerId =
    PlayerId(operatorId)

final case class RequestDictionaryNamespaceRequest(
    operatorId: String,
    namespacePrefix: String,
    contextClubId: Option[String] = None,
    ownerPlayerId: Option[String] = None,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    editorPlayerIds: Vector[String] = Vector.empty,
    note: Option[String] = None,
    reviewDueAt: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def owner: Option[PlayerId] =
    ownerPlayerId.map(PlayerId(_))

  def contextClub: Option[ClubId] =
    contextClubId.map(ClubId(_))

  def coOwners: Vector[PlayerId] =
    coOwnerPlayerIds.map(PlayerId(_)).distinct

  def editors: Vector[PlayerId] =
    editorPlayerIds.map(PlayerId(_)).distinct

  def parsedReviewDueAt: Option[java.time.Instant] =
    reviewDueAt.map(java.time.Instant.parse)

final case class ReviewDictionaryNamespaceRequest(
    operatorId: String,
    namespacePrefix: String,
    approve: Boolean,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class TransferDictionaryNamespaceRequest(
    operatorId: String,
    namespacePrefix: String,
    newOwnerPlayerId: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def newOwner: PlayerId =
    PlayerId(newOwnerPlayerId)

final case class UpdateDictionaryNamespaceCollaboratorsRequest(
    operatorId: String,
    namespacePrefix: String,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    editorPlayerIds: Vector[String] = Vector.empty,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def coOwners: Vector[PlayerId] =
    coOwnerPlayerIds.map(PlayerId(_)).distinct

  def editors: Vector[PlayerId] =
    editorPlayerIds.map(PlayerId(_)).distinct

final case class UpdateDictionaryNamespaceContextRequest(
    operatorId: String,
    namespacePrefix: String,
    contextClubId: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def contextClub: Option[ClubId] =
    contextClubId.map(ClubId(_))

final case class ProcessDictionaryNamespaceRemindersRequest(
    operatorId: String,
    asOf: Option[String] = None,
    dueSoonHours: Int = 24,
    reminderIntervalHours: Int = 12,
    escalationGraceHours: Int = 72
):
  require(dueSoonHours > 0, "Dictionary namespace dueSoonHours must be positive")
  require(reminderIntervalHours > 0, "Dictionary namespace reminderIntervalHours must be positive")
  require(escalationGraceHours > 0, "Dictionary namespace escalationGraceHours must be positive")

  def operator: PlayerId =
    PlayerId(operatorId)

  def parsedAsOf: Option[java.time.Instant] =
    asOf.map(java.time.Instant.parse)

final case class RevokeDictionaryNamespaceRequest(
    operatorId: String,
    namespacePrefix: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class UpsertDictionaryRequest(
    operatorId: String,
    key: String,
    value: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class BanPlayerRequest(
    operatorId: String,
    reason: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class DissolveClubRequest(
    operatorId: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class GrantSuperAdminRequest(
    operatorId: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class RecomputeAdvancedStatsRequest(
    operatorId: String,
    mode: String = AdvancedStatsBackfillMode.Full.toString,
    ownerType: Option[String] = None,
    ownerId: Option[String] = None,
    reason: Option[String] = None,
    limit: Int = 500
):
  require(ownerType.nonEmpty == ownerId.nonEmpty, "ownerType and ownerId must be provided together")
  require(limit > 0, "Advanced stats recompute limit must be positive")

  def operator: PlayerId =
    PlayerId(operatorId)

  def parsedMode: AdvancedStatsBackfillMode =
    AdvancedStatsBackfillMode.valueOf(mode)

final case class ProcessAdvancedStatsTasksRequest(
    operatorId: String,
    limit: Int = 50
):
  require(limit > 0, "Advanced stats task processing limit must be positive")

  def operator: PlayerId =
    PlayerId(operatorId)

final case class ReplayDomainEventOutboxRequest(
    operatorId: String,
    replayAt: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def replayAtInstant: Option[Instant] =
    replayAt.map(Instant.parse)

final case class AcknowledgeDomainEventOutboxRequest(
    operatorId: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class QuarantineDomainEventOutboxRequest(
    operatorId: String,
    reason: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

final case class BatchReplayDomainEventOutboxRequest(
    operatorId: String,
    recordIds: Vector[String],
    replayAt: Option[String] = None,
    note: Option[String] = None
):
  require(recordIds.nonEmpty, "Batch replay requires at least one recordId")

  def operator: PlayerId =
    PlayerId(operatorId)

  def records: Vector[DomainEventOutboxRecordId] =
    recordIds.map(DomainEventOutboxRecordId(_)).distinct

  def replayAtInstant: Option[Instant] =
    replayAt.map(Instant.parse)

final case class BatchAcknowledgeDomainEventOutboxRequest(
    operatorId: String,
    recordIds: Vector[String],
    note: Option[String] = None
):
  require(recordIds.nonEmpty, "Batch acknowledge requires at least one recordId")

  def operator: PlayerId =
    PlayerId(operatorId)

  def records: Vector[DomainEventOutboxRecordId] =
    recordIds.map(DomainEventOutboxRecordId(_)).distinct

final case class BatchQuarantineDomainEventOutboxRequest(
    operatorId: String,
    recordIds: Vector[String],
    reason: String
):
  require(recordIds.nonEmpty, "Batch quarantine requires at least one recordId")

  def operator: PlayerId =
    PlayerId(operatorId)

  def records: Vector[DomainEventOutboxRecordId] =
    recordIds.map(DomainEventOutboxRecordId(_)).distinct

object ApiModels:
  given ReadWriter[ApiError] = macroRW
  given ReadWriter[ApiMessage] = macroRW
  given ReadWriter[HealthResponse] = macroRW
  given [T: Reader]: Reader[PagedResponse[T]] = macroR
  given [T: Writer]: Writer[PagedResponse[T]] = macroW
  given ReadWriter[CreatePlayerRequest] = macroRW
  given ReadWriter[RegisterAccountRequest] = macroRW
  given ReadWriter[LoginRequest] = macroRW
  given ReadWriter[CreateClubRequest] = macroRW
  given ReadWriter[CreateGuestSessionRequest] = macroRW
  given ReadWriter[RevokeGuestSessionRequest] = macroRW
  given ReadWriter[UpgradeGuestSessionRequest] = macroRW
  given ReadWriter[AddClubMemberRequest] = macroRW
  given ReadWriter[ClubMembershipApplicationRequest] = macroRW
  given ReadWriter[ApproveClubApplicationRequest] = macroRW
  given ReadWriter[AssignClubAdminRequest] = macroRW
  given ReadWriter[AssignClubTitleRequest] = macroRW
  given ReadWriter[ClearClubTitleRequest] = macroRW
  given ReadWriter[AdjustClubTreasuryRequest] = macroRW
  given ReadWriter[AdjustClubPointPoolRequest] = macroRW
  given ReadWriter[AdjustClubMemberContributionRequest] = macroRW
  given ReadWriter[ClubRankNodeRequest] = macroRW
  given ReadWriter[UpdateClubRankTreeRequest] = macroRW
  given ReadWriter[AwardClubHonorRequest] = macroRW
  given ReadWriter[RevokeClubHonorRequest] = macroRW
  given ReadWriter[UpdateClubRecruitmentPolicyRequest] = macroRW
  given ReadWriter[CreateTournamentStageRequest] = macroRW
  given ReadWriter[CreateTournamentRequest] = macroRW
  given ReadWriter[ConfigureStageRulesRequest] = macroRW
  given ReadWriter[StageLineupSeatRequest] = macroRW
  given ReadWriter[SubmitStageLineupRequest] = macroRW
  given ReadWriter[AssignTournamentAdminRequest] = macroRW
  given ReadWriter[OperatorRequest] = macroRW
  given ReadWriter[UpdateOwnTableReadyStateRequest] = macroRW
  given ReadWriter[RejectClubApplicationRequest] = macroRW
  given ReadWriter[ReviewClubApplicationRequest] = macroRW
  given ReadWriter[WithdrawClubApplicationRequest] = macroRW
  given ReadWriter[UpdateClubRelationRequest] = macroRW
  given ReadWriter[AppealAttachmentRequest] = macroRW
  given ReadWriter[FileAppealRequest] = macroRW
  given ReadWriter[ResolveAppealRequest] = macroRW
  given ReadWriter[AdjudicateAppealRequest] = macroRW
  given ReadWriter[UpdateAppealWorkflowRequest] = macroRW
  given ReadWriter[ReopenAppealRequest] = macroRW
  given ReadWriter[UploadPaifuRequest] = macroRW
  given ReadWriter[CompleteStageRequest] = macroRW
  given ReadWriter[AdvanceKnockoutStageRequest] = macroRW
  given ReadWriter[SettlementAdjustmentRequest] = macroRW
  given ReadWriter[SettleTournamentRequest] = macroRW
  given ReadWriter[FinalizeTournamentSettlementRequest] = macroRW
  given ReadWriter[ForceResetTableRequest] = macroRW
  given ReadWriter[UpdateTableSeatStateRequest] = macroRW
  given ReadWriter[UpsertDictionaryRequest] = macroRW
  given ReadWriter[BanPlayerRequest] = macroRW
  given ReadWriter[DissolveClubRequest] = macroRW
  given ReadWriter[GrantSuperAdminRequest] = macroRW
  given ReadWriter[RecomputeAdvancedStatsRequest] = macroRW
  given ReadWriter[RequestDictionaryNamespaceRequest] = macroRW
  given ReadWriter[ReviewDictionaryNamespaceRequest] = macroRW
  given ReadWriter[TransferDictionaryNamespaceRequest] = macroRW
  given ReadWriter[UpdateDictionaryNamespaceCollaboratorsRequest] = macroRW
  given ReadWriter[UpdateDictionaryNamespaceContextRequest] = macroRW
  given ReadWriter[RevokeDictionaryNamespaceRequest] = macroRW
  given ReadWriter[ProcessDictionaryNamespaceRemindersRequest] = macroRW
  given ReadWriter[ProcessAdvancedStatsTasksRequest] = macroRW
  given ReadWriter[ReplayDomainEventOutboxRequest] = macroRW
  given ReadWriter[AcknowledgeDomainEventOutboxRequest] = macroRW
  given ReadWriter[QuarantineDomainEventOutboxRequest] = macroRW
  given ReadWriter[BatchReplayDomainEventOutboxRequest] = macroRW
  given ReadWriter[BatchAcknowledgeDomainEventOutboxRequest] = macroRW
  given ReadWriter[BatchQuarantineDomainEventOutboxRequest] = macroRW
  given ReadWriter[ClubTournamentParticipationStatus] =
    readwriter[String].bimap[ClubTournamentParticipationStatus](_.toString, ClubTournamentParticipationStatus.valueOf)
  given ReadWriter[TournamentParticipantClubView] = macroRW
  given ReadWriter[TournamentParticipantPlayerView] = macroRW
  given ReadWriter[TournamentWhitelistSummaryView] = macroRW
  given ReadWriter[TournamentLineupSubmissionView] = macroRW
  given ReadWriter[TournamentOperationsStageView] = macroRW
  given ReadWriter[TournamentDetailView] = macroRW
  given ReadWriter[TournamentMutationView] = macroRW
  given ReadWriter[ClubTournamentParticipationView] = macroRW

