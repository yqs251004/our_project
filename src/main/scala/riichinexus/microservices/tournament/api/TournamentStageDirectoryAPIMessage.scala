package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage


import riichinexus.microservices.tournament.objects.stage.apiTypes.TournamentStageDirectoryEntry

import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import upickle.default.ReadWriter

/** 获取赛事阶段目录。 */
final case class TournamentStageDirectoryAPIMessage(tournamentId: String) extends APIMessage[Vector[TournamentStageDirectoryEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[TournamentStageDirectoryEntry]] =
    for
      id <- IO.blocking(TournamentId(tournamentId))
      stages <- IO.blocking(resolveStages(context, id))
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
