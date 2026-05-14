package riichinexus.microservices.opsanalytics.api

import java.time.Instant
import java.util.NoSuchElementException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean

import riichinexus.application.ports.*
import riichinexus.domain.model.*

private object AdvancedStatsAsyncOutbox:
  private val executor =
    Executors.newCachedThreadPool(new ThreadFactory:
      override def newThread(runnable: Runnable): Thread =
        val thread = Thread(runnable, "riichinexus-advanced-stats-outbox")
        thread.setDaemon(true)
        thread
    )

  def submit(task: => Unit): Unit =
    executor.execute(() => task)

final class AdvancedStatsPipelineService(
    paifuRepository: PaifuRepository,
    matchRecordRepository: MatchRecordRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
    transactionManager: TransactionManager
):
  import AdvancedStatsSupport.*
  private val drainInFlight = AtomicBoolean(false)
  private val retryDelay = java.time.Duration.ofMinutes(5)
  private val maxAttempts = 3
  private val asyncDrainStartupDelay =
    if transactionManager == NoOpTransactionManager then java.time.Duration.ofMillis(50)
    else java.time.Duration.ZERO

  def enqueueImpactedOwners(
      matchRecord: MatchRecord,
      requestedAt: Instant,
      reason: String = "match-record-archived"
  ): Vector[AdvancedStatsRecomputeTask] =
    val impactedPlayers = matchRecord.playerIds.distinct
    val impactedClubs = impactedPlayers
      .flatMap(playerId => playerRepository.findById(playerId).toVector.flatMap(_.boundClubIds))
      .distinct

    (impactedPlayers.map(playerId => DashboardOwner.Player(playerId)) ++
      impactedClubs.map(clubId => DashboardOwner.Club(clubId)))
      .distinct
      .map(owner =>
        enqueueOwnerRecompute(
          owner = owner,
          reason = reason,
          requestedAt = requestedAt,
          lastMatchRecordId = Some(matchRecord.id)
        )
      )

  def enqueueFullRecompute(
      requestedAt: Instant = Instant.now(),
      reason: String = "manual-full-recompute"
  ): Vector[AdvancedStatsRecomputeTask] =
    val owners =
      playerRepository.findAll().map(player => DashboardOwner.Player(player.id)) ++
        clubRepository.findActive().map(club => DashboardOwner.Club(club.id))

    owners.distinct.map(owner => enqueueOwnerRecompute(owner, reason, requestedAt))

  def enqueueBackfill(
      mode: AdvancedStatsBackfillMode,
      requestedAt: Instant = Instant.now(),
      reason: String = "manual-backfill-recompute",
      limit: Int = 500
  ): Vector[AdvancedStatsRecomputeTask] =
    val owners =
      playerRepository.findAll().map(player => DashboardOwner.Player(player.id)) ++
        clubRepository.findActive().map(club => DashboardOwner.Club(club.id))

    owners.distinct
      .filter(owner => shouldBackfillOwner(owner, mode))
      .take(limit)
      .map(owner => enqueueOwnerRecompute(owner, reason, requestedAt))

  def enqueueOwnerRecompute(
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant = Instant.now(),
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    val task = transactionManager.inTransaction {
      advancedStatsRecomputeTaskRepository
        .findActiveByOwner(owner, AdvancedStatsBoard.CurrentCalculatorVersion)
        .getOrElse(
          advancedStatsRecomputeTaskRepository.save(
            AdvancedStatsRecomputeTask.create(
              owner = owner,
              reason = reason,
              requestedAt = requestedAt,
              calculatorVersion = AdvancedStatsBoard.CurrentCalculatorVersion,
              lastMatchRecordId = lastMatchRecordId
            )
          )
        )
    }
    scheduleAsyncDrain()
    task

  def processPending(
      limit: Int = 50,
      processedAt: Instant = Instant.now()
  ): Vector[AdvancedStatsRecomputeTask] =
    advancedStatsRecomputeTaskRepository.findPending(limit, processedAt).flatMap { task =>
      val maybeProcessing =
        try Some(advancedStatsRecomputeTaskRepository.save(task.markProcessing(processedAt)))
        catch
          case _: OptimisticConcurrencyException =>
            None

      maybeProcessing.map { processing =>
        try
          processing.owner match
            case DashboardOwner.Player(playerId) =>
              advancedStatsBoardRepository.save(rebuildPlayerBoard(playerId, processedAt))
            case DashboardOwner.Club(clubId) =>
              val club = clubRepository.findById(clubId).getOrElse(
                throw NoSuchElementException(s"Club ${clubId.value} was not found")
              )
              advancedStatsBoardRepository.save(rebuildClubBoard(club, processedAt))

          try advancedStatsRecomputeTaskRepository.save(processing.markCompleted(processedAt))
          catch
            case _: OptimisticConcurrencyException =>
              advancedStatsRecomputeTaskRepository.findById(processing.id).getOrElse(processing)
        catch
          case _: OptimisticConcurrencyException =>
            advancedStatsRecomputeTaskRepository.findById(processing.id).getOrElse(processing)
          case error: Throwable =>
            val errorMessage = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
            val failedTask =
              if processing.attempts >= maxAttempts then
                advancedStatsRecomputeTaskRepository.save(processing.markDeadLetter(errorMessage, processedAt))
              else
                val retryAt = processedAt.plus(retryDelay)
                val scheduled = advancedStatsRecomputeTaskRepository.save(
                  processing.markRetryScheduled(errorMessage, processedAt, retryAt)
                )
                scheduleAsyncDrain(Some(retryAt))
                scheduled
            failedTask
      }
    }

  def taskQueueSummary(
      asOf: Instant = Instant.now()
  ): AdvancedStatsTaskQueueSummary =
    val tasks = advancedStatsRecomputeTaskRepository.findAll()
    AdvancedStatsTaskQueueSummary(
      asOf = asOf,
      runnablePendingCount = tasks.count(_.isRunnable(asOf)),
      scheduledRetryCount = tasks.count(task =>
        task.status == AdvancedStatsRecomputeTaskStatus.Pending &&
          task.nextAttemptAt.exists(_.isAfter(asOf))
      ),
      processingCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Processing),
      completedCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Completed),
      failedCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.Failed),
      deadLetterCount = tasks.count(_.status == AdvancedStatsRecomputeTaskStatus.DeadLetter),
      oldestRunnableRequestedAt = tasks.filter(_.isRunnable(asOf)).map(_.requestedAt).sorted.headOption,
      nextScheduledRetryAt = tasks
        .filter(task => task.status == AdvancedStatsRecomputeTaskStatus.Pending)
        .flatMap(_.nextAttemptAt)
        .filter(_.isAfter(asOf))
        .sorted
        .headOption,
      newestCompletedAt = tasks.flatMap(_.completedAt).sorted.lastOption
    )

  def rebuildPlayerBoard(
      playerId: PlayerId,
      at: Instant = Instant.now()
  ): AdvancedStatsBoard =
    val records = matchRecordRepository.findByPlayer(playerId)
    val paifus = paifuRepository.findByPlayer(playerId)
    val existingVersion =
      advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
    buildPlayerBoard(playerId, records, paifus, at).copy(version = existingVersion)

  def rebuildClubBoard(
      club: Club,
      at: Instant = Instant.now()
  ): AdvancedStatsBoard =
    val memberBoards = club.members.flatMap { playerId =>
      playerRepository.findById(playerId)
        .filter(_.status == PlayerStatus.Active)
        .map(_ => rebuildPlayerBoard(playerId, at))
    }
    val existingVersion =
      advancedStatsBoardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    buildClubBoard(club, memberBoards, at).copy(version = existingVersion)

  private def scheduleAsyncDrain(notBefore: Option[Instant] = None): Unit =
    if drainInFlight.compareAndSet(false, true) then
      AdvancedStatsAsyncOutbox.submit {
        try
          if !asyncDrainStartupDelay.isZero then
            Thread.sleep(asyncDrainStartupDelay.toMillis)
          notBefore.foreach { scheduledAt =>
            val sleepMillis = java.time.Duration.between(Instant.now(), scheduledAt).toMillis
            if sleepMillis > 0 then Thread.sleep(sleepMillis)
          }
          drainLoop()
        finally
          drainInFlight.set(false)
          val now = Instant.now()
          if advancedStatsRecomputeTaskRepository.findPending(1, now).nonEmpty then
            scheduleAsyncDrain()
          else
            advancedStatsRecomputeTaskRepository.findAll()
              .filter(task => task.status == AdvancedStatsRecomputeTaskStatus.Pending)
              .flatMap(_.nextAttemptAt)
              .filter(_.isAfter(now))
              .sorted
              .headOption
              .foreach(nextAt => scheduleAsyncDrain(Some(nextAt)))
      }

  private def drainLoop(): Unit =
    var keepWorking = true
    while keepWorking do
      val now = Instant.now()
      val processed = processPending(limit = 25, processedAt = now)
      if processed.nonEmpty then keepWorking = true
      else
        val nextRetryAt = advancedStatsRecomputeTaskRepository.findAll()
          .filter(task => task.status == AdvancedStatsRecomputeTaskStatus.Pending)
          .flatMap(_.nextAttemptAt)
          .filter(_.isAfter(now))
          .sorted
          .headOption

        nextRetryAt match
          case Some(retryAt) if java.time.Duration.between(now, retryAt).compareTo(java.time.Duration.ofSeconds(10)) <= 0 =>
            val sleepMillis = math.max(1L, java.time.Duration.between(now, retryAt).toMillis)
            Thread.sleep(sleepMillis)
            keepWorking = true
          case _ =>
            keepWorking = false

  private def shouldBackfillOwner(owner: DashboardOwner, mode: AdvancedStatsBackfillMode): Boolean =
    val board = advancedStatsBoardRepository.findByOwner(owner)
    mode match
      case AdvancedStatsBackfillMode.Full    => true
      case AdvancedStatsBackfillMode.Missing => board.isEmpty
      case AdvancedStatsBackfillMode.Stale =>
        board.exists(_.calculatorVersion < AdvancedStatsBoard.CurrentCalculatorVersion)
