package riichinexus.microservices.opsanalytics.api.`private`
import riichinexus.microservices.player.api.`private`.{ApplyPlayerEloDeltaPrivateAPIMessage, ResolvePlayerBoundClubIdsPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.api.`private`.{ApplyClubPointDeltaPrivateAPIMessage, RefreshClubPowerRatingPrivateAPIMessage}
import riichinexus.microservices.opsanalytics.domain.functions.RatingService
import riichinexus.microservices.opsanalytics.domain.model.RatingChange
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.microservices.tournament.objects.`private`.matchrecord.MatchRecordPrivateView
/** 供赛事归档流程刷新赛后运营分析读模型。 */
final case class RefreshOpsAnalyticsAfterMatchArchivedPrivateAPIMessage(
    matchRecord: MatchRecordPrivateView,
    occurredAt: java.time.Instant
) extends APIMessage[Unit]:

  override def plan(context: ApiPlanContext): IO[Unit] =
    val impactedPlayerIds = matchRecord.seatResults.map(_.playerId).distinct
    val representedClubIds = matchRecord.seatResults.flatMap(_.clubId).distinct

    for
      memberClubIds <- resolveMemberClubIds(context)
      impactedClubIds = (representedClubIds ++ memberClubIds).distinct
      _ <- applyRepresentedClubPointDeltas(context)
      players <- resolveImpactedPlayers(context)
      ratingDeltas = RatingService.calculateDeltas(players, matchRecord.seatResults)
      _ <- applyPlayerRatingDeltas(context, ratingDeltas)
      _ <- notifyRatingChanges(context, players, ratingDeltas)
      refreshedClubIds <- refreshClubPowerRatings(context, impactedClubIds)
      _ <- rebuildPlayerReadModels(context, impactedPlayerIds)
      _ <- rebuildClubReadModels(context, refreshedClubIds)
    yield ()

  private def resolveMemberClubIds(context: ApiPlanContext): IO[Vector[ClubId]] =
    matchRecord.seatResults.foldLeft(IO.pure(Vector.empty[ClubId])) { (previous, result) =>
      previous.flatMap { clubIds =>
        ResolvePlayerBoundClubIdsPrivateAPIMessage(result.playerId)
          .plan(context)
          .map(playerClubIds => (clubIds ++ playerClubIds).distinct)
      }
    }

  private def applyRepresentedClubPointDeltas(context: ApiPlanContext): IO[Unit] =
    matchRecord.seatResults.foldLeft(IO.unit) { (previous, result) =>
      previous.flatMap { _ =>
        result.clubId match
          case Some(clubId) =>
            ApplyClubPointDeltaPrivateAPIMessage(clubId, result.scoreDelta).plan(context).void
          case None =>
            IO.unit
      }
    }

  private def resolveImpactedPlayers(context: ApiPlanContext): IO[Vector[PlayerPrivateView]] =
    matchRecord.seatResults.foldLeft(IO.pure(Vector.empty[PlayerPrivateView])) { (previous, result) =>
      previous.flatMap { players =>
        ResolvePlayerPrivateAPIMessage(result.playerId).plan(context).map(player => players ++ player.toVector)
      }
    }

  private def applyPlayerRatingDeltas(
      context: ApiPlanContext,
      ratingDeltas: Vector[RatingChange]
  ): IO[Unit] =
    ratingDeltas.foldLeft(IO.unit) { (previous, delta) =>
      previous.flatMap(_ => ApplyPlayerEloDeltaPrivateAPIMessage(delta.playerId, delta.delta).plan(context))
    }

  private def notifyRatingChanges(
      context: ApiPlanContext,
      players: Vector[PlayerPrivateView],
      ratingDeltas: Vector[RatingChange]
  ): IO[Unit] =
    RecordBulkNotificationsPrivateAPIMessage(
      eloChangeNotifications(matchRecord, players, ratingDeltas)
    ).plan(context).void

  private def refreshClubPowerRatings(
      context: ApiPlanContext,
      clubIds: Vector[ClubId]
  ): IO[Vector[ClubId]] =
    clubIds.foldLeft(IO.pure(Vector.empty[ClubId])) { (previous, clubId) =>
      previous.flatMap { refreshedClubIds =>
        RefreshClubPowerRatingPrivateAPIMessage(clubId)
          .plan(context)
          .map(_.fold(refreshedClubIds)(_ => refreshedClubIds :+ clubId))
      }
    }

  private def rebuildPlayerReadModels(
      context: ApiPlanContext,
      playerIds: Vector[riichinexus.microservices.player.objects.playerprofile.PlayerId]
  ): IO[Unit] =
    playerIds.foldLeft(IO.unit) { (previous, playerId) =>
      previous.flatMap { _ =>
        for
          _ <- RecordPlayerDashboardPrivateAPIMessage(playerId, occurredAt).plan(context)
          _ <- RecordPlayerAdvancedStatsBoardPrivateAPIMessage(playerId, occurredAt).plan(context)
        yield ()
      }
    }

  private def rebuildClubReadModels(
      context: ApiPlanContext,
      clubIds: Vector[ClubId]
  ): IO[Unit] =
    clubIds.foldLeft(IO.unit) { (previous, clubId) =>
      previous.flatMap { _ =>
        for
          _ <- RecordClubDashboardPrivateAPIMessage(clubId, occurredAt).plan(context)
          _ <- RecordClubAdvancedStatsBoardPrivateAPIMessage(clubId, occurredAt).plan(context)
        yield ()
      }
    }

  private def eloChangeNotifications(
      matchRecord: MatchRecordPrivateView,
      players: Vector[PlayerPrivateView],
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
          notificationType = NotificationType.PlayerEloChanged,
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
