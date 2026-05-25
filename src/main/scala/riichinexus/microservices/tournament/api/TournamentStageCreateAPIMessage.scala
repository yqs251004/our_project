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

final case class TournamentStageCreateAPIMessage(tournamentId: String, request: CreateTournamentStageRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO(request.operator.map(context.support.principal).getOrElse(AccessPrincipal.system))
      module = context.support.tournamentModule
      command = CreateStageCommand(
        tournamentId = TournamentId(tournamentId),
        actor = actor,
        stage = request.toStage
      )
      tournament <- IO {
        module.transactionManager.inTransaction {
          createStage(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def createStage(
      module: TournamentModuleContext,
      command: CreateStageCommand
  ): Option[Tournament] =
    module.tournamentRepository.findById(command.tournamentId).map { tournament =>
      ensureStageCanBeAdded(module, tournament, command)
      val dictionarySnapshot = RuntimeDictionary.snapshot(module.globalDictionaryRepository)
      module.tournamentRepository.save(tournament.addStage(normalizeStage(command.stage, dictionarySnapshot)))
    }

  private def ensureStageCanBeAdded(
      module: TournamentModuleContext,
      tournament: Tournament,
      command: CreateStageCommand
  ): Unit =
    if tournament.status == TournamentStatus.Completed || tournament.status == TournamentStatus.Archived then
      throw IllegalArgumentException(
        s"Cannot add stages to tournament ${command.tournamentId.value} in status ${tournament.status}"
      )
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
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

  private final case class CreateStageCommand(
      tournamentId: TournamentId,
      actor: AccessPrincipal,
      stage: TournamentStage
  )
