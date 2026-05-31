package riichinexus.microservices.opsanalytics.projections

import java.sql.Connection

import riichinexus.application.ports.{DomainEventSubscriber, DomainEventSubscriberPartitionStrategy}
import riichinexus.application.ports.DomainEvent
import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.domain.model.Club
import riichinexus.microservices.club.domain.ClubPowerRatingService
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.domain.MatchRecordArchived

final class ClubProjectionSubscriber extends DomainEventSubscriber:
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

        matchRecord.seatResults.foreach { result =>
          result.clubId.foreach { clubId =>
            ClubTable.findById(connection, clubId).foreach { club =>
              ClubTable.save(connection, club.addPoints(result.scoreDelta))
            }
          }
        }

        impactedClubIds.foreach { clubId =>
          ClubTable.findById(connection, clubId).foreach { club =>
            ClubTable.save(connection, recalculateClubPowerRating(connection, club))
          }
        }

      case _ =>
        ()

  private def recalculateClubPowerRating(
      connection: Connection,
      club: Club
  ): Club =
    club.updatePowerRating(
      ClubPowerRatingService.calculate(club, findPlayer(connection, _))
    )

  private def findPlayer(connection: Connection, playerId: PlayerId) =
    PlayerTable.findById(connection, playerId)
