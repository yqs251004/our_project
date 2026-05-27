package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.TournamentFormat
import upickle.default.*

final case class CreateTournamentStageRequest(
    id: Option[String] = None,
    name: String,
    format: TournamentFormat,
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
    val stageFormat = TournamentFormat.toStageFormat(format)
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

object CreateTournamentStageRequest:
  given ReadWriter[CreateTournamentStageRequest] = macroRW
