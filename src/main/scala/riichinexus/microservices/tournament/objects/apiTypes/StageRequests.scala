package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

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
    note: Option[String] = None,
    pairingMethod: Option[String] = None,
    carryOverPoints: Option[Boolean] = None,
    maxRounds: Option[Int] = None,
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Option[Boolean] = None,
    repechageEnabled: Option[Boolean] = None,
    seedingPolicy: Option[String] = None,
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
            templateKey = ruleTemplateKey,
            note = note
          )
        )
        .getOrElse(
          AdvancementRule.defaultFor(stageFormat).copy(
            templateKey = ruleTemplateKey,
            note = note.orElse(AdvancementRule.defaultFor(stageFormat).note)
          )
        ),
      swissRule =
        if pairingMethod.isDefined || carryOverPoints.isDefined || maxRounds.isDefined then
          Some(
            SwissRuleConfig(
              pairingMethod = pairingMethod.map(_.trim.toLowerCase).getOrElse("balanced-elo"),
              carryOverPoints = carryOverPoints.getOrElse(true),
              maxRounds = maxRounds
            )
          )
        else None,
      knockoutRule =
        if bracketSize.isDefined || thirdPlaceMatch.isDefined || seedingPolicy.isDefined || repechageEnabled.isDefined then
          Some(
            KnockoutRuleConfig(
              bracketSize = bracketSize,
              thirdPlaceMatch = thirdPlaceMatch.getOrElse(false),
              seedingPolicy = seedingPolicy.map(_.trim.toLowerCase).getOrElse("rating"),
              repechageEnabled = repechageEnabled.getOrElse(false)
            )
          )
        else None,
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

final case class CompleteStageRequest(
    operatorId: Option[String] = None
):
  def operator: Option[PlayerId] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))

final case class AdvanceKnockoutStageRequest(
    operatorId: Option[String] = None
):
  def operator: Option[PlayerId] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))

object StageRequests:
  given ReadWriter[CreateTournamentStageRequest] = macroRW
  given ReadWriter[CreateTournamentRequest] = macroRW
  given ReadWriter[ConfigureStageRulesRequest] = macroRW
  given ReadWriter[StageLineupSeatRequest] = macroRW
  given ReadWriter[SubmitStageLineupRequest] = macroRW
  given ReadWriter[CompleteStageRequest] = macroRW
  given ReadWriter[AdvanceKnockoutStageRequest] = macroRW
