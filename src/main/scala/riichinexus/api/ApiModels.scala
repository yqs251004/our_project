package riichinexus.api

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ApiError(
    message: String
)

final case class ApiMessage(
    message: String
)

final case class HealthResponse(
    status: String,
    storage: String,
    timestamp: Instant
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

final case class CreateClubRequest(
    name: String,
    creatorId: String
):
  def creator: PlayerId =
    PlayerId(creatorId)

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
    message: Option[String] = None
)

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

final case class CreateTournamentStageRequest(
    id: Option[String],
    name: String,
    format: String,
    order: Int,
    roundCount: Int,
    advancementRuleType: Option[String] = None,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    schedulingPoolSize: Option[Int] = None
):
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
            targetTableCount = targetTableCount
          )
        )
        .getOrElse(AdvancementRule.defaultFor(stageFormat)),
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
    advancementRuleType: String,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    schedulingPoolSize: Int = 4,
    carryOverPoints: Option[Boolean] = None,
    maxRounds: Option[Int] = None,
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Option[Boolean] = None,
    seedingPolicy: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def advancementRule: AdvancementRule =
    AdvancementRule(
      ruleType = AdvancementRuleType.valueOf(advancementRuleType),
      cutSize = cutSize,
      thresholdScore = thresholdScore,
      targetTableCount = targetTableCount,
      note = note
    )

  def swissRule: Option[SwissRuleConfig] =
    if carryOverPoints.isDefined || maxRounds.isDefined then
      Some(
        SwissRuleConfig(
          carryOverPoints = carryOverPoints.getOrElse(true),
          maxRounds = maxRounds
        )
      )
    else None

  def knockoutRule: Option[KnockoutRuleConfig] =
    if bracketSize.isDefined || thirdPlaceMatch.isDefined || seedingPolicy.isDefined then
      Some(
        KnockoutRuleConfig(
          bracketSize = bracketSize,
          thirdPlaceMatch = thirdPlaceMatch.getOrElse(false),
          seedingPolicy = seedingPolicy.getOrElse("rating")
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

final case class AppealAttachmentRequest(
    name: String,
    uri: String,
    contentType: Option[String] = None
):
  def toAttachment: AppealAttachment =
    AppealAttachment(name, uri, contentType)

final case class FileAppealRequest(
    playerId: String,
    description: String,
    attachments: Vector[AppealAttachmentRequest] = Vector.empty
):
  def player: PlayerId =
    PlayerId(playerId)

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
    payoutRatios: Vector[Double] = Vector(0.5, 0.3, 0.2)
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def stageId: TournamentStageId =
    TournamentStageId(finalStageId)

final case class ForceResetTableRequest(
    operatorId: String,
    note: String
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

object ApiModels:
  given ReadWriter[ApiError] = macroRW
  given ReadWriter[ApiMessage] = macroRW
  given ReadWriter[HealthResponse] = macroRW
  given ReadWriter[CreatePlayerRequest] = macroRW
  given ReadWriter[CreateClubRequest] = macroRW
  given ReadWriter[AddClubMemberRequest] = macroRW
  given ReadWriter[ClubMembershipApplicationRequest] = macroRW
  given ReadWriter[ApproveClubApplicationRequest] = macroRW
  given ReadWriter[AssignClubAdminRequest] = macroRW
  given ReadWriter[AssignClubTitleRequest] = macroRW
  given ReadWriter[CreateTournamentStageRequest] = macroRW
  given ReadWriter[CreateTournamentRequest] = macroRW
  given ReadWriter[ConfigureStageRulesRequest] = macroRW
  given ReadWriter[StageLineupSeatRequest] = macroRW
  given ReadWriter[SubmitStageLineupRequest] = macroRW
  given ReadWriter[AssignTournamentAdminRequest] = macroRW
  given ReadWriter[AppealAttachmentRequest] = macroRW
  given ReadWriter[FileAppealRequest] = macroRW
  given ReadWriter[ResolveAppealRequest] = macroRW
  given ReadWriter[AdjudicateAppealRequest] = macroRW
  given ReadWriter[UploadPaifuRequest] = macroRW
  given ReadWriter[CompleteStageRequest] = macroRW
  given ReadWriter[AdvanceKnockoutStageRequest] = macroRW
  given ReadWriter[SettleTournamentRequest] = macroRW
  given ReadWriter[ForceResetTableRequest] = macroRW
  given ReadWriter[UpsertDictionaryRequest] = macroRW
  given ReadWriter[BanPlayerRequest] = macroRW
  given ReadWriter[DissolveClubRequest] = macroRW
