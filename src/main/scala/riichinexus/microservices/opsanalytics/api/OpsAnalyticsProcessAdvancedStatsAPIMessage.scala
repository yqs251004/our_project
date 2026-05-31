package riichinexus.microservices.opsanalytics.api

import cats.effect.unsafe.implicits.global
import riichinexus.api.ApiPlanContext
import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.bootstrap.OpsAnalyticsModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.club.api.`private`.{ListClubsPrivateAPIMessage, ResolveClubPrivateAPIMessage, ResolveClubsPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.opsanalytics.domain.AdvancedStatsRoundAnalysis.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.*
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.api.`private`.{
  ListPlayerMatchRecordsPrivateAPIMessage,
  ListPlayerPaifusPrivateAPIMessage
}
import upickle.default.*

final case class OpsAnalyticsProcessAdvancedStatsAPIMessage(
    operatorId: PlayerId,
    limit: Int = 50
) extends APIMessage[Vector[AdvancedStatsRecomputeTask]] derives ReadWriter:

  require(limit > 0, "Advanced stats task processing limit must be positive")

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTask]] =
    for
      operator <- IO.blocking(context.principal(operatorId))
      processedAt <- IO.realTimeInstant
      module = context.support.opsAnalyticsModule
      command = ProcessAdvancedStatsCommand(operator, limit, processedAt)
      _ <- IO.blocking(requireOpsAdmin(context, command.operator))
      tasks <- IO.blocking(processPending(context.connection, module, command))
    yield tasks

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ManagePlatformOperations)

  private val retryDelay = Duration.ofMinutes(5)
  private val maxAttempts = 3

  private def processPending(
      connection: java.sql.Connection,
      module: OpsAnalyticsModuleContext,
      command: ProcessAdvancedStatsCommand
  ): Vector[AdvancedStatsRecomputeTask] =
    riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.findPending(connection, command.limit, command.processedAt).flatMap { task =>
      val maybeProcessing =
        try Some(riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(connection, task.markProcessing(command.processedAt)))
        catch
          case _: OptimisticConcurrencyException =>
            None

      maybeProcessing.map { processing =>
        try
          processing.owner match
            case DashboardOwner.Player(playerId) =>
              riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.save(connection, rebuildPlayerBoard(connection, module, playerId, command.processedAt))
            case DashboardOwner.Club(clubId) =>
              val club = ResolveClubPrivateAPIMessage(clubId).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync().getOrElse(
                throw NoSuchElementException(s"Club ${clubId.value} was not found")
              )
              riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.save(connection, rebuildClubBoard(connection, module, club, command.processedAt))

          try riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(connection, processing.markCompleted(command.processedAt))
          catch
            case _: OptimisticConcurrencyException =>
              riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.findById(connection, processing.id).getOrElse(processing)
        catch
          case _: OptimisticConcurrencyException =>
            riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.findById(connection, processing.id).getOrElse(processing)
          case error: Throwable =>
            val errorMessage = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
            if processing.attempts >= maxAttempts then
              riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(connection, processing.markDeadLetter(errorMessage, command.processedAt))
            else
              val retryAt = command.processedAt.plus(retryDelay)
              riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(connection, 
                processing.markRetryScheduled(errorMessage, command.processedAt, retryAt)
              )
      }
    }

  private def rebuildPlayerBoard(
      connection: java.sql.Connection,
      module: OpsAnalyticsModuleContext,
      playerId: PlayerId,
      at: Instant
  ): AdvancedStatsBoard =
    val records = ListPlayerMatchRecordsPrivateAPIMessage(playerId)
      .plan(ApiPlanContext(support = null, bearerToken = None, connection = connection))
      .unsafeRunSync()
    val paifus = ListPlayerPaifusPrivateAPIMessage(playerId)
      .plan(ApiPlanContext(support = null, bearerToken = None, connection = connection))
      .unsafeRunSync()
    val existingVersion =
      riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.findByOwner(connection, DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
    buildPlayerBoard(playerId, records, paifus, at).copy(version = existingVersion)

  private def rebuildClubBoard(
      connection: java.sql.Connection,
      module: OpsAnalyticsModuleContext,
      club: Club,
      at: Instant
  ): AdvancedStatsBoard =
    val memberBoards = club.members.flatMap { playerId =>
      GetPlayerAPIMessage.findPlayer(connection, playerId)
        .filter(_.status == PlayerStatus.Active)
        .map(_ => rebuildPlayerBoard(connection, module, playerId, at))
    }
    val existingVersion =
      riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.findByOwner(connection, DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    buildClubBoard(club, memberBoards, at).copy(version = existingVersion)

  private final case class ProcessAdvancedStatsCommand(
      operator: AccessPrincipal,
      limit: Int,
      processedAt: Instant
  )
