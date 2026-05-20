package riichinexus.microservices.opsanalytics.projections

import riichinexus.application.ports.{
  ClubRepository,
  DomainEventSubscriber,
  DomainEventSubscriberPartitionStrategy,
  GlobalDictionaryRepository,
  PlayerRepository
}
import riichinexus.domain.event.*
import riichinexus.domain.model.Club
import riichinexus.microservices.dictionary.domain.RuntimeDictionary

final class ClubProjectionSubscriber(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    globalDictionaryRepository: GlobalDictionaryRepository
) extends DomainEventSubscriber:
  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, _) =>
        val representedClubIds = matchRecord.seatResults.flatMap(_.clubId).distinct
        val memberClubIds = matchRecord.seatResults.flatMap { result =>
          playerRepository.findById(result.playerId).toVector.flatMap(_.boundClubIds)
        }.distinct
        val impactedClubIds = (representedClubIds ++ memberClubIds).distinct
        val dictionarySnapshot = RuntimeDictionary.snapshot(globalDictionaryRepository)

        matchRecord.seatResults.foreach { result =>
          result.clubId.foreach { clubId =>
            clubRepository.findById(clubId).foreach { club =>
              clubRepository.save(club.addPoints(result.scoreDelta))
            }
          }
        }

        impactedClubIds.foreach { clubId =>
          clubRepository.findById(clubId).foreach { club =>
            clubRepository.save(recalculateClubPowerRating(club, dictionarySnapshot))
          }
        }

      case _ =>
        ()

  private def recalculateClubPowerRating(
      club: Club,
      dictionarySnapshot: RuntimeDictionary.DictionarySnapshot
  ): Club =
    club.updatePowerRating(
      RuntimeDictionary.calculateClubPowerRating(club, playerRepository, dictionarySnapshot)
    )
