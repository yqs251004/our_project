package riichinexus.microservices.opsanalytics.projections

import riichinexus.application.ports.{DomainEventSubscriber, DomainEventSubscriberPartitionStrategy, PlayerRepository}
import riichinexus.domain.event.*
import riichinexus.domain.service.RatingService

final class RatingProjectionSubscriber(
    playerRepository: PlayerRepository,
    ratingService: RatingService
) extends DomainEventSubscriber:
  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, _) =>
        val players = matchRecord.seatResults.flatMap { result =>
          playerRepository.findById(result.playerId)
        }

        val deltas = ratingService.calculateDeltas(players, matchRecord.seatResults)

        deltas.foreach { delta =>
          playerRepository.findById(delta.playerId).foreach { player =>
            playerRepository.save(player.applyElo(delta.delta))
          }
        }

      case _ =>
        ()
