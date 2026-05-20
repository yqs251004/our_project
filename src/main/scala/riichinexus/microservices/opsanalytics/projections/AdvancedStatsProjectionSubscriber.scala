package riichinexus.microservices.opsanalytics.projections

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.application.ports.{DomainEventSubscriber, DomainEventSubscriberPartitionStrategy}
import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.AdvancedStatsRoundAnalysis

final class AdvancedStatsProjectionSubscriber(
    paifuRepository: PaifuRepository,
    matchRecordRepository: MatchRecordRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
    transactionManager: TransactionManager
) extends DomainEventSubscriber:
  import AdvancedStatsRoundAnalysis.*

  private val retryDelay = Duration.ofMinutes(5)
  private val maxAttempts = 3

  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, occurredAt) =>
        enqueueImpactedOwners(matchRecord, occurredAt)
        processPending(limit = 25, processedAt = occurredAt)
        ()
      case _ =>
        ()

  private def enqueueImpactedOwners(
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

  private def enqueueOwnerRecompute(
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant,
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    transactionManager.inTransaction {
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

  private def processPending(
      limit: Int,
      processedAt: Instant
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
            if processing.attempts >= maxAttempts then
              advancedStatsRecomputeTaskRepository.save(processing.markDeadLetter(errorMessage, processedAt))
            else
              val retryAt = processedAt.plus(retryDelay)
              advancedStatsRecomputeTaskRepository.save(
                processing.markRetryScheduled(errorMessage, processedAt, retryAt)
              )
      }
    }

  private def rebuildPlayerBoard(
      playerId: PlayerId,
      at: Instant
  ): AdvancedStatsBoard =
    val records = matchRecordRepository.findByPlayer(playerId)
    val paifus = paifuRepository.findByPlayer(playerId)
    val existingVersion =
      advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
    buildPlayerBoard(playerId, records, paifus, at).copy(version = existingVersion)

  private def rebuildClubBoard(
      club: Club,
      at: Instant
  ): AdvancedStatsBoard =
    val memberBoards = club.members.flatMap { playerId =>
      playerRepository.findById(playerId)
        .filter(_.status == PlayerStatus.Active)
        .map(_ => rebuildPlayerBoard(playerId, at))
    }
    val existingVersion =
      advancedStatsBoardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    buildClubBoard(club, memberBoards, at).copy(version = existingVersion)
