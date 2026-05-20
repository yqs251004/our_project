package riichinexus.microservices.opsanalytics.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.bootstrap.OpsAnalyticsModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.AdvancedStatsRoundAnalysis.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class OpsAnalyticsProcessAdvancedStatsAPIMessage(
    operatorId: PlayerId,
    limit: Int = 50
) extends APIMessage[Vector[AdvancedStatsRecomputeTask]] derives ReadWriter:

  require(limit > 0, "Advanced stats task processing limit must be positive")

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTask]] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
      processPending(context.support.opsAnalyticsModule, limit = limit, processedAt = Instant.now())
    }

  private val retryDelay = Duration.ofMinutes(5)
  private val maxAttempts = 3

  private def processPending(
      module: OpsAnalyticsModuleContext,
      limit: Int,
      processedAt: Instant
  ): Vector[AdvancedStatsRecomputeTask] =
    module.advancedStatsRecomputeTaskRepository.findPending(limit, processedAt).flatMap { task =>
      val maybeProcessing =
        try Some(module.advancedStatsRecomputeTaskRepository.save(task.markProcessing(processedAt)))
        catch
          case _: OptimisticConcurrencyException =>
            None

      maybeProcessing.map { processing =>
        try
          processing.owner match
            case DashboardOwner.Player(playerId) =>
              module.advancedStatsBoardRepository.save(rebuildPlayerBoard(module, playerId, processedAt))
            case DashboardOwner.Club(clubId) =>
              val club = module.clubRepository.findById(clubId).getOrElse(
                throw NoSuchElementException(s"Club ${clubId.value} was not found")
              )
              module.advancedStatsBoardRepository.save(rebuildClubBoard(module, club, processedAt))

          try module.advancedStatsRecomputeTaskRepository.save(processing.markCompleted(processedAt))
          catch
            case _: OptimisticConcurrencyException =>
              module.advancedStatsRecomputeTaskRepository.findById(processing.id).getOrElse(processing)
        catch
          case _: OptimisticConcurrencyException =>
            module.advancedStatsRecomputeTaskRepository.findById(processing.id).getOrElse(processing)
          case error: Throwable =>
            val errorMessage = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
            if processing.attempts >= maxAttempts then
              module.advancedStatsRecomputeTaskRepository.save(processing.markDeadLetter(errorMessage, processedAt))
            else
              val retryAt = processedAt.plus(retryDelay)
              module.advancedStatsRecomputeTaskRepository.save(
                processing.markRetryScheduled(errorMessage, processedAt, retryAt)
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
      module: OpsAnalyticsModuleContext,
      club: Club,
      at: Instant
  ): AdvancedStatsBoard =
    val memberBoards = club.members.flatMap { playerId =>
      module.playerRepository.findById(playerId)
        .filter(_.status == PlayerStatus.Active)
        .map(_ => rebuildPlayerBoard(module, playerId, at))
    }
    val existingVersion =
      module.advancedStatsBoardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    buildClubBoard(club, memberBoards, at).copy(version = existingVersion)
