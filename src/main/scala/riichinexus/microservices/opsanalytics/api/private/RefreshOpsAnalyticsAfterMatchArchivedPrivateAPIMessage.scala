package riichinexus.microservices.opsanalytics.api.`private`
import riichinexus.microservices.player.api.`private`.*

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.api.`private`.{ResolveClubPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.ClubPowerRatingService
import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.microservices.opsanalytics.domain.functions.RatingService
import riichinexus.microservices.opsanalytics.domain.model.RatingChange
import riichinexus.microservices.notification.api.`private`.CreateBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
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
      memberClubIds <- matchRecord.seatResults.foldLeft(IO.pure(Vector.empty[ClubId])) { (previous, result) =>
        previous.flatMap { clubIds =>
          ResolvePlayerPrivateAPIMessage(result.playerId).plan(context).map(player =>
            (clubIds ++ player.toVector.flatMap(PlayerClubBindingFunctions.boundClubIds)).distinct
          )
        }
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
      players <- matchRecord.seatResults.foldLeft(IO.pure(Vector.empty[riichinexus.microservices.player.domain.Player])) { (previous, result) =>
        previous.flatMap { players =>
          ResolvePlayerPrivateAPIMessage(result.playerId).plan(context).map(player => players ++ player.toVector)
        }
      }
      ratingDeltas = RatingService.calculateDeltas(players, matchRecord.seatResults)
      ratingNotificationRequests = eloChangeNotifications(matchRecord, players, ratingDeltas)
      _ <- ratingDeltas.foldLeft(IO.unit) { (previous, delta) =>
        previous.flatMap { _ =>
          ResolvePlayerPrivateAPIMessage(delta.playerId).plan(context).flatMap {
            case Some(player) =>
              SavePlayerPrivateAPIMessage(PlayerRatingFunctions.applyElo(player, delta.delta)).plan(context).map(_ => ())
            case None =>
              IO.unit
          }
        }
      }
      _ <- CreateBulkNotificationsPrivateAPIMessage(ratingNotificationRequests).plan(context)
      refreshedClubs <- impactedClubIds.foldLeft(IO.pure(Vector.empty[Club])) { (previous, clubId) =>
        previous.flatMap { clubs =>
          ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap {
            case Some(club) =>
              club.members.foldLeft(IO.pure(Map.empty[PlayerId, riichinexus.microservices.player.domain.Player])) { (playersIO, playerId) =>
                playersIO.flatMap { playersById =>
                  ResolvePlayerPrivateAPIMessage(playerId).plan(context).map {
                    case Some(player) => playersById + (playerId -> player)
                    case None         => playersById
                  }
                }
              }.flatMap { playersById =>
                val refreshed = ClubFunctions.updatePowerRating(
                  club,
                  ClubPowerRatingService.calculate(club, playerId => playersById.get(playerId))
                )
                SaveClubPrivateAPIMessage(refreshed).plan(context).map(saved => clubs :+ saved)
              }
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

  private def eloChangeNotifications(
      matchRecord: MatchRecord,
      players: Vector[riichinexus.microservices.player.domain.Player],
      ratingDeltas: Vector[RatingChange]
  ): Vector[CreateNotificationRequest] =
    val playersById = players.map(player => player.id -> player).toMap
    ratingDeltas.filter(_.delta != 0).flatMap { delta =>
      playersById.get(delta.playerId).map { player =>
        val nextElo = player.elo + delta.delta
        val deltaText =
          if delta.delta > 0 then s"+${delta.delta}"
          else delta.delta.toString
        CreateNotificationRequest(
          recipientPlayerId = delta.playerId.value,
          notificationType = "PlayerEloChanged",
          title = "ELO 已更新",
          body = s"本场对局结算后，你的 ELO $deltaText，当前 ELO $nextElo。",
          severity = Some("info"),
          sourceService = "opsanalytics",
          sourceType = "player-rating",
          sourceId = matchRecord.id.value,
          actionUrl = Some(s"/public/tournaments/${matchRecord.tournamentId.value}"),
          objects = Map(
            "tournamentId" -> matchRecord.tournamentId.value,
            "stageId" -> matchRecord.stageId.value,
            "tableId" -> matchRecord.tableId.value,
            "matchRecordId" -> matchRecord.id.value,
            "playerId" -> delta.playerId.value,
            "eloDelta" -> delta.delta.toString,
            "elo" -> nextElo.toString
          )
        )
      }
    }
