package riichinexus.microservices.tournament.api.competition
import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType, StructuredEventField}
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.TournamentSummaryView

/** 登记选手参与赛事并发送邀请通知。 */
final case class TournamentRegisterPlayerAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView]:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- resolveOperatorActor(context)
      requestedTournamentId = TournamentId(tournamentId)
      requestedPlayerId = PlayerId(playerId)
      registration <- registerPlayer(context, requestedTournamentId, requestedPlayerId, actor).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      notificationRequests =
        if registration._2 then playerInvitationNotifications(registration._1, registration._3)
        else Vector.empty
      _ <- RecordBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
    yield TournamentViewFunctions.tournamentSummaryView(registration._1)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def registerPlayer(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  ): IO[Option[(Tournament, Boolean, PlayerPrivateView)]] =
    val connection = context.connection
    for
      player <- ResolvePlayerPrivateAPIMessage(playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${playerId.value} was not found")))
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.ManageTournamentStages, tournamentId = Some(tournamentId)).plan(context)
      result <- IO.blocking {
        ensurePlayerCanEnter(player, tournamentId, playerId)
        TournamentTable.findById(connection, tournamentId).map { tournament =>
          val wasNewInvitation =
            !tournament.whitelist.exists(_.playerId.contains(playerId)) &&
              !tournament.participatingPlayers.contains(playerId)
          val updatedTournament =
            TournamentTable.save(connection, TournamentFunctions.registerPlayer(tournament, playerId))
          (updatedTournament, wasNewInvitation, player)
        }
      }
    yield result

  private def playerInvitationNotifications(
      tournament: Tournament,
      player: PlayerPrivateView
  ): Vector[CreateNotificationRequest] =
    Vector(
      CreateNotificationRequest(
        recipientPlayerId = player.id.value,
        notificationType = NotificationType.TournamentPlayerInvited,
        title = "收到赛事邀请",
        body = "你被邀请参加赛事 " + tournament.name + "。",
        severity = Some(NotificationSeverity.Info),
        sourceService = NotificationSourceService.Tournament,
        sourceType = NotificationSourceType.TournamentPlayerInvitation,
        sourceId = tournament.id.value,
        actionUrl = Some("/public/tournaments/" + tournament.id.value),
        objects = Map(
          StructuredEventField.toString(StructuredEventField.TournamentId) -> tournament.id.value,
          StructuredEventField.toString(StructuredEventField.PlayerId) -> player.id.value
        )
      )
    )

  private def ensurePlayerCanEnter(player: PlayerPrivateView, tournamentId: TournamentId, playerId: PlayerId): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"PlayerPrivateView ${playerId.value} cannot enter tournament ${tournamentId.value}")
