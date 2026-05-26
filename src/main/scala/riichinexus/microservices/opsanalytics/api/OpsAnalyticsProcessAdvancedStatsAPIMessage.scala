package riichinexus.microservices.opsanalytics.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.bootstrap.OpsAnalyticsModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.domain.service.AdvancedStatsRoundAnalysis.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.*
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class OpsAnalyticsProcessAdvancedStatsAPIMessage(
    operatorId: PlayerId,
    limit: Int = 50
) extends APIMessage[Vector[AdvancedStatsRecomputeTask]] derives ReadWriter:

  require(limit > 0, "Advanced stats task processing limit must be positive")

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTask]] =
    for
      operator <- IO(context.principal(operatorId))
      processedAt <- IO.realTimeInstant
      module = context.support.opsAnalyticsModule
      command = ProcessAdvancedStatsCommand(operator, limit, processedAt)
      _ <- IO(requireOpsAdmin(context, command.operator))
      tasks <- IO(processPending(context.connection, module, command))
    yield tasks

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)

  private val retryDelay = Duration.ofMinutes(5)
  private val maxAttempts = 3

  private def processPending(
      connection: java.sql.Connection,
      module: OpsAnalyticsModuleContext,
      command: ProcessAdvancedStatsCommand
  ): Vector[AdvancedStatsRecomputeTask] =
    module.advancedStatsRecomputeTaskRepository.findPending(command.limit, command.processedAt).flatMap { task =>
      val maybeProcessing =
        try Some(module.advancedStatsRecomputeTaskRepository.save(task.markProcessing(command.processedAt)))
        catch
          case _: OptimisticConcurrencyException =>
            None

      maybeProcessing.map { processing =>
        try
          processing.owner match
            case DashboardOwner.Player(playerId) =>
              module.advancedStatsBoardRepository.save(rebuildPlayerBoard(module, playerId, command.processedAt))
            case DashboardOwner.Club(clubId) =>
              val club = module.clubRepository.findById(clubId).getOrElse(
                throw NoSuchElementException(s"Club ${clubId.value} was not found")
              )
              module.advancedStatsBoardRepository.save(rebuildClubBoard(connection, module, club, command.processedAt))

          try module.advancedStatsRecomputeTaskRepository.save(processing.markCompleted(command.processedAt))
          catch
            case _: OptimisticConcurrencyException =>
              module.advancedStatsRecomputeTaskRepository.findById(processing.id).getOrElse(processing)
        catch
          case _: OptimisticConcurrencyException =>
            module.advancedStatsRecomputeTaskRepository.findById(processing.id).getOrElse(processing)
          case error: Throwable =>
            val errorMessage = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
            if processing.attempts >= maxAttempts then
              module.advancedStatsRecomputeTaskRepository.save(processing.markDeadLetter(errorMessage, command.processedAt))
            else
              val retryAt = command.processedAt.plus(retryDelay)
              module.advancedStatsRecomputeTaskRepository.save(
                processing.markRetryScheduled(errorMessage, command.processedAt, retryAt)
              )
      }
    }

  private def rebuildPlayerBoard(
      module: OpsAnalyticsModuleContext,
      playerId: PlayerId,
      at: Instant
  ): AdvancedStatsBoard =
    val records = module.matchRecordRepository.findByPlayer(playerId)
    val paifus = module.paifuRepository.findByPlayer(playerId)
    val existingVersion =
      module.advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
    buildPlayerBoard(playerId, records, paifus, at).copy(version = existingVersion)

  private def rebuildClubBoard(
      connection: java.sql.Connection,
      module: OpsAnalyticsModuleContext,
      club: Club,
      at: Instant
  ): AdvancedStatsBoard =
    val memberBoards = club.members.flatMap { playerId =>
      PlayerTable.findById(connection, playerId)
        .filter(_.status == PlayerStatus.Active)
        .map(_ => rebuildPlayerBoard(module, playerId, at))
    }
    val existingVersion =
      module.advancedStatsBoardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    buildClubBoard(club, memberBoards, at).copy(version = existingVersion)

  private final case class ProcessAdvancedStatsCommand(
      operator: AccessPrincipal,
      limit: Int,
      processedAt: Instant
  )
