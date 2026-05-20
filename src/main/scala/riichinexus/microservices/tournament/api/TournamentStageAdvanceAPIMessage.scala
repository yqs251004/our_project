package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentStageAdvanceAPIMessage(tournamentId: String, stageId: String, request: AdvanceKnockoutStageRequest) extends APIMessage[Vector[Table]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Table]] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val stageIdValue = TournamentStageId(stageId)
      val actor = request.operator.map(context.support.principal).getOrElse(AccessPrincipal.system)
      val at = Instant.now()

      module.transactionManager.inTransaction {
        val tournament = module.tournamentRepository
          .findById(tournamentIdValue)
          .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentIdValue.value} was not found"))
        val stage = tournament.stages
          .find(_.id == stageIdValue)
          .getOrElse(throw NoSuchElementException(s"Stage ${stageIdValue.value} was not found"))

        module.authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentIdValue)
        )

        val isKnockoutStage =
          stage.format == StageFormat.Knockout ||
            stage.format == StageFormat.Finals ||
            stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

        if !isKnockoutStage then
          throw IllegalArgumentException(
            s"Stage ${stageIdValue.value} is not configured as a knockout stage"
          )

        module.knockoutStageCoordinator.materializeUnlockedTables(tournamentIdValue, stageIdValue, at)
      }
    }
