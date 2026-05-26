package riichinexus.microservices.opsanalytics.projections

import java.sql.Connection

import riichinexus.application.ports.{DomainEventSubscriber, DomainEventSubscriberPartitionStrategy}
import riichinexus.domain.event.*
import riichinexus.domain.service.RatingService
import riichinexus.microservices.player.tables.player.PlayerTable

final class RatingProjectionSubscriber(
    ratingService: RatingService
) extends DomainEventSubscriber:
  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(connection: Connection, event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, _) =>
        val players = matchRecord.seatResults.flatMap { result =>
          PlayerTable.findById(connection, result.playerId)
        }

        val deltas = ratingService.calculateDeltas(players, matchRecord.seatResults)

        deltas.foreach { delta =>
          PlayerTable.findById(connection, delta.playerId).foreach { player =>
            PlayerTable.save(connection, player.applyElo(delta.delta))
          }
        }

      case _ =>
        ()
