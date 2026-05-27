package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.TournamentFormat
import upickle.default.*

final case class ConfigureStageRulesRequest(
    operatorId: String,
    format: Option[TournamentFormat] = None,
    roundCount: Option[Int] = None,
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

  def stageFormat: Option[StageFormat] =
    format.map(TournamentFormat.toStageFormat)

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

object ConfigureStageRulesRequest:
  given ReadWriter[ConfigureStageRulesRequest] = macroRW
