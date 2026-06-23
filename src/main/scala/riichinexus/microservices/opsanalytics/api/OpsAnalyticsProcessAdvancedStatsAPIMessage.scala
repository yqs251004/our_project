package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.errors.OptimisticConcurrencyException
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.api.audit.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.profile.`private`.ClubPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsBoardFunctions
import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsRecomputeTaskFunctions
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, AdvancedStatsRecomputeTask, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
import riichinexus.microservices.tournament.api.matchrecord.`private`.LoadPlayerMatchRecordsForOpsAnalyticsPrivateAPIMessage
import riichinexus.microservices.tournament.api.paifu.`private`.LoadPlayerPaifusForOpsAnalyticsPrivateAPIMessage
/** 处理高级统计重算任务队列。 */
final case class OpsAnalyticsProcessAdvancedStatsAPIMessage(
    operatorId: PlayerId,
    limit: Int = 50
) extends APIMessage[Vector[AdvancedStatsRecomputeTask]]:

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTask]] =
    for
      _ <- IO.delay(validateLimit())
      operator <- ResolveAccessPrincipalPrivateAPIMessage(operatorId).plan(context)
      processedAt <- IO.realTimeInstant
      _ <- RequirePermissionPrivateAPIMessage(operator, Permission.ManagePlatformOperations).plan(context)
      pendingTasks <- loadPendingTasks(context, limit, processedAt)
      processedTasks <- processTasks(context, pendingTasks, processedAt)
    yield processedTasks

  private def validateLimit(): Unit =
    if limit <= 0 then throw IllegalArgumentException("Advanced stats task processing limit must be positive")

  private val retryDelay = Duration.ofMinutes(5)
  private val maxAttempts = 3

  private def loadPendingTasks(
      context: ApiPlanContext,
      limit: Int,
      processedAt: Instant
  ): IO[Vector[AdvancedStatsRecomputeTask]] =
    IO.blocking(AdvancedStatsRecomputeTaskTable.findPending(context.connection, limit, processedAt))

  private def processTasks(
      context: ApiPlanContext,
      pendingTasks: Vector[AdvancedStatsRecomputeTask],
      processedAt: Instant
  ): IO[Vector[AdvancedStatsRecomputeTask]] =
    pendingTasks.foldLeft(IO.pure(Vector.empty[AdvancedStatsRecomputeTask])) { (previous, task) =>
      previous.flatMap(tasks => processTask(context, task, processedAt).map(_.fold(tasks)(tasks :+ _)))
    }

  private def processTask(
      context: ApiPlanContext,
      task: AdvancedStatsRecomputeTask,
      processedAt: Instant
  ): IO[Option[AdvancedStatsRecomputeTask]] =
    for
      claimedTask <- claimTask(context, task, processedAt)
      processedTask <- claimedTask match
        case Some(processing) => processClaimedTask(context, processing, processedAt).map(Some(_))
        case None             => IO.pure(None)
    yield processedTask

  private def processClaimedTask(
      context: ApiPlanContext,
      processing: AdvancedStatsRecomputeTask,
      processedAt: Instant
  ): IO[AdvancedStatsRecomputeTask] =
    processMarkedTask(context, processing, processedAt).attempt.flatMap {
      case Right(completed) => IO.pure(completed)
      case Left(_: OptimisticConcurrencyException) =>
        loadLatestTask(context, processing)
      case Left(error) =>
        markTaskFailed(context, processing, processedAt, error)
    }

  private def claimTask(
      context: ApiPlanContext,
      task: AdvancedStatsRecomputeTask,
      processedAt: Instant
  ): IO[Option[AdvancedStatsRecomputeTask]] =
    IO.blocking {
      try
        Some(
          AdvancedStatsRecomputeTaskTable.save(
            context.connection,
            AdvancedStatsRecomputeTaskFunctions.markProcessing(task, processedAt)
          )
        )
      catch
        case _: OptimisticConcurrencyException => None
    }

  private def loadLatestTask(
      context: ApiPlanContext,
      fallback: AdvancedStatsRecomputeTask
  ): IO[AdvancedStatsRecomputeTask] =
    IO.blocking(AdvancedStatsRecomputeTaskTable.findById(context.connection, fallback.id).getOrElse(fallback))

  private def markTaskFailed(
      context: ApiPlanContext,
      processing: AdvancedStatsRecomputeTask,
      processedAt: Instant,
      error: Throwable
  ): IO[AdvancedStatsRecomputeTask] =
    val errorMessage = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
    IO.blocking {
      if processing.attempts >= maxAttempts then
          AdvancedStatsRecomputeTaskTable.save(
            context.connection,
            AdvancedStatsRecomputeTaskFunctions.markDeadLetter(processing, errorMessage, processedAt)
          )
      else
        val retryAt = processedAt.plus(retryDelay)
        AdvancedStatsRecomputeTaskTable.save(
          context.connection,
          AdvancedStatsRecomputeTaskFunctions.markRetryScheduled(
            processing,
            errorMessage,
            processedAt,
            retryAt
          )
        )
    }

  private def processMarkedTask(
      context: ApiPlanContext,
      processing: AdvancedStatsRecomputeTask,
      processedAt: Instant
  ): IO[AdvancedStatsRecomputeTask] =
    for
      board <- rebuildBoardForOwner(context, processing.owner, processedAt)
      _ <- saveBoard(context, board)
      completed <- markTaskCompleted(context, processing, processedAt)
    yield completed

  private def rebuildBoardForOwner(
      context: ApiPlanContext,
      owner: DashboardOwner,
      at: Instant
  ): IO[AdvancedStatsBoard] =
    owner match
      case DashboardOwner.Player(playerId) =>
        rebuildPlayerBoard(context, playerId, at)
      case DashboardOwner.Club(clubId) =>
        for
          club <- resolveClub(context, clubId)
          board <- rebuildClubBoard(context, club, at)
        yield board

  private def resolveClub(context: ApiPlanContext, clubId: ClubId): IO[ClubPrivateView] =
    ResolveClubReadModelsPrivateAPIMessage(Vector(clubId))
      .plan(context)
      .map(_.headOption.getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found")))

  private def saveBoard(context: ApiPlanContext, board: AdvancedStatsBoard): IO[Unit] =
    IO.blocking(AdvancedStatsBoardTable.save(context.connection, board)).void

  private def markTaskCompleted(
      context: ApiPlanContext,
      processing: AdvancedStatsRecomputeTask,
      processedAt: Instant
  ): IO[AdvancedStatsRecomputeTask] =
    IO.blocking {
      try
          AdvancedStatsRecomputeTaskTable.save(
            context.connection,
          AdvancedStatsRecomputeTaskFunctions.markCompleted(processing, processedAt)
        )
      catch
        case _: OptimisticConcurrencyException =>
          AdvancedStatsRecomputeTaskTable.findById(context.connection, processing.id).getOrElse(processing)
    }

  private def rebuildPlayerBoard(
      context: ApiPlanContext,
      playerId: PlayerId,
      at: Instant
  ): IO[AdvancedStatsBoard] =
    val connection = context.connection
    for
      records <- LoadPlayerMatchRecordsForOpsAnalyticsPrivateAPIMessage(playerId).plan(context)
      paifus <- LoadPlayerPaifusForOpsAnalyticsPrivateAPIMessage(playerId).plan(context)
      existingVersion <- loadBoardVersion(context, DashboardOwner.Player(playerId))
    yield AdvancedStatsBoardFunctions.buildPlayerBoard(playerId, records, paifus, at, existingVersion)

  private def rebuildClubBoard(
      context: ApiPlanContext,
      club: ClubPrivateView,
      at: Instant
  ): IO[AdvancedStatsBoard] =
    for
      activeMemberIds <- resolveActiveClubMemberIds(context, club)
      memberBoards <- rebuildMemberBoards(context, activeMemberIds, at)
      existingVersion <- loadBoardVersion(context, DashboardOwner.Club(club.id))
    yield AdvancedStatsBoardFunctions.buildClubBoard(club.id, memberBoards, at, existingVersion)

  private def resolveActiveClubMemberIds(
      context: ApiPlanContext,
      club: ClubPrivateView
  ): IO[Vector[PlayerId]] =
    club.members.foldLeft(IO.pure(Vector.empty[PlayerId])) { (previous, playerId) =>
      previous.flatMap { playerIds =>
        ResolvePlayerPrivateAPIMessage(playerId).plan(context).map {
          case Some(player) if player.status == PlayerStatus.Active => playerIds :+ playerId
          case _                                                    => playerIds
        }
      }
    }

  private def rebuildMemberBoards(
      context: ApiPlanContext,
      memberIds: Vector[PlayerId],
      at: Instant
  ): IO[Vector[AdvancedStatsBoard]] =
    memberIds.foldLeft(IO.pure(Vector.empty[AdvancedStatsBoard])) { (previous, playerId) =>
      previous.flatMap(boards => rebuildPlayerBoard(context, playerId, at).map(board => boards :+ board))
    }

  private def loadBoardVersion(context: ApiPlanContext, owner: DashboardOwner): IO[Int] =
    IO.blocking(AdvancedStatsBoardTable.findByOwner(context.connection, owner).map(_.version).getOrElse(0))
