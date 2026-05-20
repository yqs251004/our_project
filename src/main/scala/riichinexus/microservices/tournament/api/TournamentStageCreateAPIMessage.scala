package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.RuntimeDictionary
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentStageCreateAPIMessage(tournamentId: String, request: CreateTournamentStageRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val actor = request.operator.map(context.support.principal).getOrElse(AccessPrincipal.system)

      module.transactionManager.inTransaction {
        module.tournamentRepository.findById(tournamentIdValue).map { tournament =>
          if tournament.status == TournamentStatus.Completed || tournament.status == TournamentStatus.Archived then
            throw IllegalArgumentException(
              s"Cannot add stages to tournament ${tournamentIdValue.value} in status ${tournament.status}"
            )

          module.authorizationService.requirePermission(
            actor,
            Permission.ManageTournamentStages,
            tournamentId = Some(tournamentIdValue)
          )

          val dictionarySnapshot = RuntimeDictionary.snapshot(module.globalDictionaryRepository)
          TournamentSummaryView.fromDomain(
            module.tournamentRepository.save(tournament.addStage(normalizeStage(request.toStage, dictionarySnapshot)))
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
