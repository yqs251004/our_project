package riichinexus.microservices.opsanalytics.projections

import java.sql.Connection

import riichinexus.application.ports.{DomainEventSubscriber, DomainEventSubscriberPartitionStrategy}
import riichinexus.application.ports.DomainEvent
import riichinexus.microservices.opsanalytics.domain.RatingService
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.domain.events.MatchRecordArchived

final class RatingProjectionSubscriber extends DomainEventSubscriber:
  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(connection: Connection, event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, _) =>
        val players = matchRecord.seatResults.flatMap { result =>
          GetPlayerAPIMessage.findPlayer(connection, result.playerId)
        }

        val deltas = RatingService.calculateDeltas(players, matchRecord.seatResults)

        deltas.foreach { delta =>
          GetPlayerAPIMessage.findPlayer(connection, delta.playerId).foreach { player =>
            CreatePlayerAPIMessage.persistPlayer(connection, player.applyElo(delta.delta))
          }
        }

      case _ =>
        ()
