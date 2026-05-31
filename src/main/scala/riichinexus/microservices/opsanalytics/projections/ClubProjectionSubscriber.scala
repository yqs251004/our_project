package riichinexus.microservices.opsanalytics.projections

import cats.effect.unsafe.implicits.global
import riichinexus.api.ApiPlanContext
import java.sql.Connection

import riichinexus.application.ports.{DomainEventSubscriber, DomainEventSubscriberPartitionStrategy}
import riichinexus.application.ports.DomainEvent
import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.api.`private`.{ResolveClubPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.microservices.club.domain.model.Club
import riichinexus.microservices.club.domain.ClubPowerRatingService
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.domain.events.MatchRecordArchived

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
            ResolveClubPrivateAPIMessage(clubId).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync().foreach { club =>
              SaveClubPrivateAPIMessage(club.addPoints(result.scoreDelta))
                .plan(ApiPlanContext(support = null, bearerToken = None, connection = connection))
                .unsafeRunSync()
            }
          }
        }

        impactedClubIds.foreach { clubId =>
          ResolveClubPrivateAPIMessage(clubId).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync().foreach { club =>
            SaveClubPrivateAPIMessage(recalculateClubPowerRating(connection, club))
              .plan(ApiPlanContext(support = null, bearerToken = None, connection = connection))
              .unsafeRunSync()
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
    GetPlayerAPIMessage.findPlayer(connection, playerId)
