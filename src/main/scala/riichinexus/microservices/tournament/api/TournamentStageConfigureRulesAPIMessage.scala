package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
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
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val stageIdValue = TournamentStageId(stageId)
      val actor = context.support.principal(request.operator)

      module.transactionManager.inTransaction {
        module.tournamentRepository.findById(tournamentIdValue).map { tournament =>
          val currentStage = tournament.stages
            .find(_.id == stageIdValue)
            .getOrElse(throw NoSuchElementException(s"Stage ${stageIdValue.value} was not found"))
          module.authorizationService.requirePermission(
            actor,
            Permission.ConfigureTournamentRules,
            tournamentId = Some(tournamentIdValue)
          )

          val dictionarySnapshot = RuntimeDictionary.snapshot(module.globalDictionaryRepository)
          val baseStage = currentStage.copy(
            format = request.stageFormat.getOrElse(currentStage.format),
            roundCount = math.max(request.roundCount.getOrElse(currentStage.roundCount), currentStage.currentRound)
          )
          val configuredStage = normalizeStage(
            baseStage.withRules(
              request.advancementRule,
              request.swissRule,
              request.knockoutRule,
              request.schedulingPoolSize.getOrElse(baseStage.schedulingPoolSize)
            ),
            dictionarySnapshot
          )

          TournamentSummaryView.fromDomain(
            module.tournamentRepository.save(
              tournament.updateStage(stageIdValue, _ => configuredStage)
            )
          )
        }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }

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
