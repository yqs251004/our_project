package riichinexus.microservices.tournament.api.stage
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import riichinexus.microservices.tournament.objects.stage.rules.progression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.stage.rules.knockout.{KnockoutRuleConfig, KnockoutSeedingPolicy}
import riichinexus.microservices.tournament.objects.stage.rules.swiss.{SwissPairingMethod, SwissRuleConfig}
import riichinexus.microservices.tournament.objects.competition.TournamentStatus

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.domain.identity.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.stage.functions.rules.progression.AdvancementRuleFunctions
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage
import riichinexus.microservices.tournament.domain.competition.model.Tournament

import riichinexus.microservices.tournament.domain.competition.functions.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.stage.lifecycle.apiTypes.CreateTournamentStageRequest
import riichinexus.microservices.tournament.objects.competition.TournamentSummaryView
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable

/** 为赛事新增阶段。 */
final case class TournamentStageCreateAPIMessage(tournamentId: String, request: CreateTournamentStageRequest) extends APIMessage[TournamentSummaryView]:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- request.operatorId.map(PlayerId(_)).map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context)).getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))
      stage <- IO.blocking(tournamentStage(request))
      requestedTournamentId = TournamentId(tournamentId)
      _ <- RequirePermissionPrivateAPIMessage(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(requestedTournamentId)
      ).plan(context)
      tournament <- IO.blocking(createStage(context.connection, requestedTournamentId, stage).getOrElse(throw NoSuchElementException("Resource not found")))
    yield TournamentViewFunctions.tournamentSummaryView(tournament)

  private def createStage(
      connection: java.sql.Connection,
      tournamentId: TournamentId,
      stage: TournamentStage
  ): Option[Tournament] =
    TournamentTable.findById(connection, tournamentId).map { tournament =>
      ensureStageCanBeAdded(tournament, tournamentId)
      TournamentTable.save(connection, TournamentFunctions.addStage(tournament, TournamentRuntimeDefaults.normalizeStage(stage)))
    }

  private def ensureStageCanBeAdded(
      tournament: Tournament,
      tournamentId: TournamentId
  ): Unit =
    if tournament.status == TournamentStatus.Completed || tournament.status == TournamentStatus.Archived then
      throw IllegalArgumentException(
        s"Cannot add stages to tournament ${tournamentId.value} in status ${tournament.status}"
      )

  private def tournamentStage(request: CreateTournamentStageRequest): TournamentStage =
    TournamentStage(
      id = TournamentIdGenerator.stageId(),
      name = request.name,
      format = request.format,
      order = request.order,
      roundCount = request.roundCount,
      advancementRule = request.advancementRuleType
        .map(rule =>
          AdvancementRule(
            ruleType = rule,
            cutSize = request.cutSize,
            thresholdScore = request.thresholdScore,
            targetTableCount = request.targetTableCount,
            templateKey = request.ruleTemplateKey,
            note = request.note
          )
        )
        .getOrElse(
          AdvancementRuleFunctions.defaultFor(request.format).copy(
            templateKey = request.ruleTemplateKey,
            note = request.note.orElse(AdvancementRuleFunctions.defaultFor(request.format).note)
          )
      ),
      swissRule = swissRule(request),
      knockoutRule = knockoutRule(request),
      mahjongRuleset = request.mahjongRuleset.getOrElse(MahjongRuleset()),
      schedulingPoolSize = request.schedulingPoolSize.getOrElse(4)
    )

  private def swissRule(request: CreateTournamentStageRequest): Option[SwissRuleConfig] =
    if request.pairingMethod.isDefined || request.carryOverPoints.isDefined || request.maxRounds.isDefined then
      Some(
        SwissRuleConfig(
          pairingMethod = request.pairingMethod.getOrElse(SwissPairingMethod.BalancedElo),
          carryOverPoints = request.carryOverPoints.getOrElse(true),
          maxRounds = request.maxRounds
        )
      )
    else None

  private def knockoutRule(request: CreateTournamentStageRequest): Option[KnockoutRuleConfig] =
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
