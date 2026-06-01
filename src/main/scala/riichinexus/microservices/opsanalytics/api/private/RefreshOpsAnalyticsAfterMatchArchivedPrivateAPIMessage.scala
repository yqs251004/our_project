package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.api.`private`.{ResolveClubPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.ClubPowerRatingService
import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.microservices.opsanalytics.domain.functions.RatingService
import riichinexus.microservices.player.domain.functions.{PlayerClubBindingFunctions, PlayerRatingFunctions}
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage}
import riichinexus.microservices.tournament.domain.recordmanagement.functions.MatchRecordFunctions
import riichinexus.microservices.tournament.domain.recordmanagement.model.MatchRecord
import upickle.default.*

final case class RefreshOpsAnalyticsAfterMatchArchivedPrivateAPIMessage(
    matchRecord: MatchRecord,
    occurredAt: java.time.Instant
) extends APIMessage[Unit] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Unit] =
    val impactedPlayerIds = MatchRecordFunctions.playerIds(matchRecord).distinct
    val representedClubIds = matchRecord.seatResults.flatMap(_.clubId).distinct

    for
      memberClubIds <- IO.blocking {
        matchRecord.seatResults.flatMap { result =>
          GetPlayerAPIMessage.findPlayer(context.connection, result.playerId).toVector.flatMap(PlayerClubBindingFunctions.boundClubIds)
        }.distinct
      }
      impactedClubIds = (representedClubIds ++ memberClubIds).distinct
      _ <- matchRecord.seatResults.foldLeft(IO.unit) { (previous, result) =>
        previous.flatMap { _ =>
          result.clubId match
            case Some(clubId) =>
              ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap {
                case Some(club) =>
                  SaveClubPrivateAPIMessage(ClubFunctions.addPoints(club, result.scoreDelta))
                    .plan(context)
                    .map(_ => ())
                case None =>
                  IO.unit
              }
            case None =>
              IO.unit
        }
      }
      players <- IO.blocking {
        matchRecord.seatResults.flatMap(result =>
          GetPlayerAPIMessage.findPlayer(context.connection, result.playerId)
        )
      }
      ratingDeltas = RatingService.calculateDeltas(players, matchRecord.seatResults)
      _ <- ratingDeltas.foldLeft(IO.unit) { (previous, delta) =>
        previous.flatMap { _ =>
          IO.blocking(GetPlayerAPIMessage.findPlayer(context.connection, delta.playerId)).flatMap {
            case Some(player) =>
              IO.blocking(CreatePlayerAPIMessage.persistPlayer(context.connection, PlayerRatingFunctions.applyElo(player, delta.delta))).map(_ => ())
            case None =>
              IO.unit
          }
        }
      }
      refreshedClubs <- impactedClubIds.foldLeft(IO.pure(Vector.empty[Club])) { (previous, clubId) =>
        previous.flatMap { clubs =>
          ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap {
            case Some(club) =>
              val refreshed = ClubFunctions.updatePowerRating(
                club,
                ClubPowerRatingService.calculate(club, GetPlayerAPIMessage.findPlayer(context.connection, _))
              )
              SaveClubPrivateAPIMessage(refreshed).plan(context).map(saved => clubs :+ saved)
            case None =>
              IO.pure(clubs)
          }
        }
      }
      _ <- impactedPlayerIds.foldLeft(IO.unit) { (previous, playerId) =>
        previous.flatMap(_ =>
          RecordPlayerDashboardAPIMessage(playerId, occurredAt).plan(context).flatMap(_ =>
            RecordPlayerAdvancedStatsBoardAPIMessage(playerId, occurredAt).plan(context).map(_ => ())
          )
        )
      }
      _ <- refreshedClubs.foldLeft(IO.unit) { (previous, club) =>
        previous.flatMap(_ =>
          RecordClubDashboardAPIMessage(club, occurredAt).plan(context).flatMap(_ =>
            RecordClubAdvancedStatsBoardAPIMessage(club, occurredAt).plan(context).map(_ => ())
          )
        )
      }
    yield ()
