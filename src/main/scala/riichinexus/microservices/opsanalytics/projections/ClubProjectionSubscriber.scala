package riichinexus.microservices.opsanalytics.projections

import java.sql.Connection

import riichinexus.application.ports.{
  ClubRepository,
  DomainEventSubscriber,
  DomainEventSubscriberPartitionStrategy,
  GlobalDictionaryRepository
}
import riichinexus.domain.event.*
import riichinexus.domain.model.{Club, PlayerId}
import riichinexus.microservices.dictionary.domain.RuntimeDictionary
import riichinexus.microservices.player.tables.player.PlayerTable

final class ClubProjectionSubscriber(
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository
) extends DomainEventSubscriber:
  override def partitionStrategy: DomainEventSubscriberPartitionStrategy =
    DomainEventSubscriberPartitionStrategy.AggregateRoot

  override def handle(connection: Connection, event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, _) =>
        val representedClubIds = matchRecord.seatResults.flatMap(_.clubId).distinct
        val memberClubIds = matchRecord.seatResults.flatMap { result =>
          findPlayer(connection, result.playerId).toVector.flatMap(_.boundClubIds)
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
            clubRepository.save(recalculateClubPowerRating(connection, club, dictionarySnapshot))
          }
        }

      case _ =>
        ()

  private def recalculateClubPowerRating(
      connection: Connection,
      club: Club,
      dictionarySnapshot: RuntimeDictionary.DictionarySnapshot
  ): Club =
    club.updatePowerRating(
      RuntimeDictionary.calculateClubPowerRating(club, findPlayer(connection, _), dictionarySnapshot)
    )

  private def findPlayer(connection: Connection, playerId: PlayerId) =
    PlayerTable.findById(connection, playerId)
