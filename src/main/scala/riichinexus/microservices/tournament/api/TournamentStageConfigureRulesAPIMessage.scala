package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.RuntimeDictionary
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{Table as _, TableSeat as _, StageStandingEntry as _, StageRankingSnapshot as _, StageAdvancementSnapshot as _, KnockoutBracketSlot as _, KnockoutBracketResult as _, KnockoutBracketMatch as _, KnockoutBracketRound as _, KnockoutBracketSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentStageConfigureRulesAPIMessage(tournamentId: String, stageId: String, request: ConfigureStageRulesRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO(context.support.principal(request.operator))
      module = context.support.tournamentModule
      command = ConfigureStageRulesCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = actor,
        request = request
      )
      tournament <- IO {
        module.transactionManager.inTransaction {
          configureStageRules(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def configureStageRules(
      module: TournamentModuleContext,
      command: ConfigureStageRulesCommand
  ): Option[Tournament] =
    module.tournamentRepository.findById(command.tournamentId).map { tournament =>
      val currentStage = requireStage(tournament, command.stageId)
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ConfigureTournamentRules,
        tournamentId = Some(command.tournamentId)
      )
      val configuredStage = buildConfiguredStage(module, currentStage, command.request)
      module.tournamentRepository.save(
        tournament.updateStage(command.stageId, _ => configuredStage)
      )
    }

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def buildConfiguredStage(
      module: TournamentModuleContext,
      currentStage: TournamentStage,
      request: ConfigureStageRulesRequest
  ): TournamentStage =
    val dictionarySnapshot = RuntimeDictionary.snapshot(module.globalDictionaryRepository)
    val baseStage = currentStage.copy(
      format = request.stageFormat.getOrElse(currentStage.format),
      roundCount = math.max(request.roundCount.getOrElse(currentStage.roundCount), currentStage.currentRound)
    )
    normalizeStage(
      baseStage.withRules(
        request.advancementRule,
        request.swissRule,
        request.knockoutRule,
        request.schedulingPoolSize.getOrElse(baseStage.schedulingPoolSize)
      ),
      dictionarySnapshot
    )

  private def normalizeStage(
      stage: TournamentStage,
      dictionarySnapshot: RuntimeDictionary.DictionarySnapshot
  ): TournamentStage =
    val templatedStage =
      RuntimeDictionary.resolveStageRules(stage, dictionarySnapshot)

    if templatedStage.advancementRule.ruleType == AdvancementRuleType.Custom &&
        templatedStage.advancementRule.note.contains("unconfigured") &&
        templatedStage.advancementRule.templateKey.isEmpty
    then templatedStage.copy(advancementRule = AdvancementRule.defaultFor(templatedStage.format))
    else templatedStage

  private final case class ConfigureStageRulesCommand(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      request: ConfigureStageRulesRequest
  )
