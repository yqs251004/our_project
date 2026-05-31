package riichinexus.microservices.opsanalytics.projections

import cats.effect.unsafe.implicits.global
import riichinexus.api.ApiPlanContext
import java.sql.Connection
import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.application.ports.{DomainEventSubscriber, DomainEventSubscriberPartitionStrategy}
import riichinexus.application.ports.*
import riichinexus.application.ports.DomainEvent
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.MatchRecordFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.club.api.`private`.{ListClubsPrivateAPIMessage, ResolveClubPrivateAPIMessage, ResolveClubsPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.opsanalytics.domain.AdvancedStatsRoundAnalysis
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, AdvancedStatsRecomputeTask, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.domain.events.MatchRecordArchived
import riichinexus.microservices.tournament.api.`private`.{
  ListPlayerMatchRecordsPrivateAPIMessage,
  ListPlayerPaifusPrivateAPIMessage
}

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
    val impactedPlayers = MatchRecordFunctions.playerIds(matchRecord).distinct
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
              val club = ResolveClubPrivateAPIMessage(clubId).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync().getOrElse(
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
    val records = ListPlayerMatchRecordsPrivateAPIMessage(playerId)
      .plan(ApiPlanContext(support = null, bearerToken = None, connection = connection))
      .unsafeRunSync()
    val paifus = ListPlayerPaifusPrivateAPIMessage(playerId)
      .plan(ApiPlanContext(support = null, bearerToken = None, connection = connection))
      .unsafeRunSync()
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
    GetPlayerAPIMessage.findPlayer(connection, playerId)
