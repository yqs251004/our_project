package riichinexus.microservices.opsanalytics.projections

import java.sql.Connection
import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.application.ports.{DomainEventSubscriber, DomainEventSubscriberPartitionStrategy}
import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.opsanalytics.domain.AdvancedStatsRoundAnalysis
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, AdvancedStatsRecomputeTask, DashboardOwner}
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.paifu.PaifuTable

final class AdvancedStatsProjectionSubscriber(
    transactionManager: TransactionManager
) extends DomainEventSubscriber:
  import AdvancedStatsRoundAnalysis.*

  private val retryDelay = Duration.ofMinutes(5)
  private val maxAttempts = 3

  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(connection: Connection, event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, occurredAt) =>
        enqueueImpactedOwners(connection, matchRecord, occurredAt)
        processPending(connection, limit = 25, processedAt = occurredAt)
        ()
      case _ =>
        ()

  private def enqueueImpactedOwners(
      connection: Connection,
      matchRecord: MatchRecord,
      requestedAt: Instant,
      reason: String = "match-record-archived"
  ): Vector[AdvancedStatsRecomputeTask] =
    val impactedPlayers = matchRecord.playerIds.distinct
    val impactedClubs = impactedPlayers
      .flatMap(playerId => findPlayer(connection, playerId).toVector.flatMap(_.boundClubIds))
      .distinct

    (impactedPlayers.map(playerId => DashboardOwner.Player(playerId)) ++
      impactedClubs.map(clubId => DashboardOwner.Club(clubId)))
      .distinct
      .map(owner =>
        enqueueOwnerRecompute(
          connection = connection,
          owner = owner,
          reason = reason,
          requestedAt = requestedAt,
          lastMatchRecordId = Some(matchRecord.id)
        )
      )

  private def enqueueOwnerRecompute(
      connection: Connection,
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant,
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    transactionManager.inTransaction {
      AdvancedStatsRecomputeTaskTable
        .findActiveByOwner(connection, owner, AdvancedStatsBoard.CurrentCalculatorVersion)
        .getOrElse(
          AdvancedStatsRecomputeTaskTable.save(
            connection,
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
      connection: Connection,
      limit: Int,
      processedAt: Instant
  ): Vector[AdvancedStatsRecomputeTask] =
    AdvancedStatsRecomputeTaskTable.findPending(connection, limit, processedAt).flatMap { task =>
      val maybeProcessing =
        try Some(AdvancedStatsRecomputeTaskTable.save(connection, task.markProcessing(processedAt)))
        catch
          case _: OptimisticConcurrencyException =>
            None

      maybeProcessing.map { processing =>
        try
          processing.owner match
            case DashboardOwner.Player(playerId) =>
              AdvancedStatsBoardTable.save(connection, rebuildPlayerBoard(connection, playerId, processedAt))
            case DashboardOwner.Club(clubId) =>
              val club = ClubTable.findById(connection, clubId).getOrElse(
                throw NoSuchElementException(s"Club ${clubId.value} was not found")
              )
              AdvancedStatsBoardTable.save(connection, rebuildClubBoard(connection, club, processedAt))

          try AdvancedStatsRecomputeTaskTable.save(connection, processing.markCompleted(processedAt))
          catch
            case _: OptimisticConcurrencyException =>
              AdvancedStatsRecomputeTaskTable.findById(connection, processing.id).getOrElse(processing)
        catch
          case _: OptimisticConcurrencyException =>
            AdvancedStatsRecomputeTaskTable.findById(connection, processing.id).getOrElse(processing)
          case error: Throwable =>
            val errorMessage = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
            if processing.attempts >= maxAttempts then
              AdvancedStatsRecomputeTaskTable.save(connection, processing.markDeadLetter(errorMessage, processedAt))
            else
              val retryAt = processedAt.plus(retryDelay)
              AdvancedStatsRecomputeTaskTable.save(
                connection,
                processing.markRetryScheduled(errorMessage, processedAt, retryAt)
              )
      }
    }

  private def rebuildPlayerBoard(
      connection: Connection,
      playerId: PlayerId,
      at: Instant
  ): AdvancedStatsBoard =
    val records = MatchRecordTable.findByPlayer(connection, playerId)
    val paifus = PaifuTable.findByPlayer(connection, playerId)
    val existingVersion =
      AdvancedStatsBoardTable.findByOwner(connection, DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
    buildPlayerBoard(playerId, records, paifus, at).copy(version = existingVersion)

  private def rebuildClubBoard(
      connection: Connection,
      club: Club,
      at: Instant
  ): AdvancedStatsBoard =
    val memberBoards = club.members.flatMap { playerId =>
      findPlayer(connection, playerId)
        .filter(_.status == PlayerStatus.Active)
        .map(_ => rebuildPlayerBoard(connection, playerId, at))
    }
    val existingVersion =
      AdvancedStatsBoardTable.findByOwner(connection, DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    buildClubBoard(club, memberBoards, at).copy(version = existingVersion)

  private def findPlayer(connection: Connection, playerId: PlayerId): Option[Player] =
    PlayerTable.findById(connection, playerId)
