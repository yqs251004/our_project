package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

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

final case class TournamentStageDirectoryAPIMessage(tournamentId: String) extends APIMessage[Vector[TournamentStageDirectoryEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[TournamentStageDirectoryEntry]] =
    IO {
      val tournamentIdValue = TournamentId(tournamentId)
      context.support.tournamentModule.tables
        .findTournament(tournamentIdValue)
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentIdValue.value} was not found"))
        .stages
        .sortBy(_.order)
        .map(buildTournamentStageDirectoryEntry)
    }

  private def buildTournamentStageDirectoryEntry(stage: TournamentStage): TournamentStageDirectoryEntry =
    TournamentStageDirectoryEntry(
      stageId = stage.id,
      name = stage.name,
      format = stage.format,
      order = stage.order,
      status = stage.status,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size
    )
