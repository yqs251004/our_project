package riichinexus.microservices.tournament.api.stage
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.stage.functions.TournamentStageFunctions
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage
import riichinexus.microservices.tournament.domain.competition.model.Tournament

import riichinexus.microservices.tournament.domain.competition.functions.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.stage.rules.progression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.stage.rules.knockout.{KnockoutRuleConfig, KnockoutSeedingPolicy}
import riichinexus.microservices.tournament.objects.stage.rules.swiss.{SwissPairingMethod, SwissRuleConfig}
import riichinexus.microservices.tournament.objects.stage.rules.apiTypes.ConfigureStageRulesRequest
import riichinexus.microservices.tournament.objects.competition.TournamentSummaryView
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable

/** 配置指定赛事阶段的规则。 */
final case class TournamentStageConfigureRulesAPIMessage(tournamentId: String, stageId: String, request: ConfigureStageRulesRequest) extends APIMessage[TournamentSummaryView]:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      requestedTournamentId = TournamentId(tournamentId)
      requestedStageId = TournamentStageId(stageId)
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.ConfigureTournamentRules, tournamentId = Some(requestedTournamentId)).plan(context)
      tournament <- IO.blocking(configureStageRules(context.connection, requestedTournamentId, requestedStageId).getOrElse(throw NoSuchElementException("Resource not found")))
    yield TournamentViewFunctions.tournamentSummaryView(tournament)

  private def configureStageRules(
      connection: java.sql.Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[Tournament] =
    TournamentTable.findById(connection, tournamentId).map { tournament =>
      val currentStage = requireStage(tournament, stageId)
      val configuredStage = buildConfiguredStage(currentStage, request)
      TournamentTable.save(connection,
        TournamentFunctions.updateStage(tournament, stageId, _ => configuredStage)
      )
    }

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def buildConfiguredStage(
      currentStage: TournamentStage,
      request: ConfigureStageRulesRequest
  ): TournamentStage =
    validateRequest(request)
    val baseStage = currentStage.copy(
      format = request.format.getOrElse(currentStage.format),
      roundCount = math.max(request.roundCount.getOrElse(currentStage.roundCount), currentStage.currentRound)
    )
    TournamentRuntimeDefaults.normalizeStage(
      TournamentStageFunctions.withRules(
        baseStage,
        advancementRule(request),
        swissRule(request),
        knockoutRule(request),
        request.schedulingPoolSize.getOrElse(baseStage.schedulingPoolSize),
        request.mahjongRuleset.getOrElse(baseStage.mahjongRuleset)
      )
    )

  private def advancementRule(request: ConfigureStageRulesRequest): AdvancementRule =
    AdvancementRule(
      ruleType = request.advancementRuleType.getOrElse(AdvancementRuleType.Custom),
      cutSize = request.cutSize,
      thresholdScore = request.thresholdScore,
      targetTableCount = request.targetTableCount,
      templateKey = request.ruleTemplateKey,
      note = request.note
    )

  private def swissRule(request: ConfigureStageRulesRequest): Option[SwissRuleConfig] =
    if request.pairingMethod.isDefined || request.carryOverPoints.isDefined || request.maxRounds.isDefined then
      Some(
        SwissRuleConfig(
          pairingMethod = request.pairingMethod.getOrElse(SwissPairingMethod.BalancedElo),
          carryOverPoints = request.carryOverPoints.getOrElse(true),
          maxRounds = request.maxRounds
        )
      )
    else None

  private def knockoutRule(request: ConfigureStageRulesRequest): Option[KnockoutRuleConfig] =
    if request.bracketSize.isDefined || request.thirdPlaceMatch.isDefined || request.seedingPolicy.isDefined || request.repechageEnabled.isDefined then
      Some(
        KnockoutRuleConfig(
          bracketSize = request.bracketSize,
          thirdPlaceMatch = request.thirdPlaceMatch.getOrElse(false),
          seedingPolicy = request.seedingPolicy.getOrElse(KnockoutSeedingPolicy.Rating),
          repechageEnabled = request.repechageEnabled.getOrElse(false)
        )
      )
    else None

  private def validateRequest(request: ConfigureStageRulesRequest): Unit =
    require(
      request.ruleTemplateKey.forall(_.trim.nonEmpty),
      "ruleTemplateKey must not be blank"
    )
    request.mahjongRuleset.foreach(validateMahjongRuleset)

  private def validateMahjongRuleset(ruleset: MahjongRuleset): Unit =
    require(ruleset.initialPoints > 0, "initialPoints must be positive")
    require(ruleset.targetPoints > 0, "targetPoints must be positive")
    require(ruleset.akaDoraCount >= 0 && ruleset.akaDoraCount <= 3, "akaDoraCount must be between 0 and 3")
    require(ruleset.minHan >= 1, "minHan must be positive")

