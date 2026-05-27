package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.TournamentFormat
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.tournament.TournamentTable
import upickle.default.*

final case class TournamentStageDirectoryAPIMessage(tournamentId: String) extends APIMessage[Vector[TournamentStageDirectoryEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[TournamentStageDirectoryEntry]] =
    for
      id <- IO(TournamentId(tournamentId))
      stages <- IO(resolveStages(context, id))
    yield stages
      .sortBy(_.order)
      .map(buildTournamentStageDirectoryEntry)

  private def resolveStages(context: ApiPlanContext, tournamentId: TournamentId): Vector[TournamentStage] =
    TournamentTable
      .findById(context.connection, tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
      .stages

  private def buildTournamentStageDirectoryEntry(stage: TournamentStage): TournamentStageDirectoryEntry =
    TournamentStageDirectoryEntry(
      stageId = stage.id.value,
      name = stage.name,
      format = TournamentFormat.fromStageFormat(stage.format),
      order = stage.order,
      status = stage.status.toString,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size
    )
