package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.TournamentSummaryView

import upickle.default.ReadWriter

/** 登记选手参与赛事并发送邀请通知。 */
final case class TournamentRegisterPlayerAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- resolveOperatorActor(context)
      command = RegisterTournamentPlayerCommand(
        tournamentId = TournamentId(tournamentId),
        playerId = PlayerId(playerId),
        actor = actor
      )
      registration <- registerPlayer(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      notificationRequests =
        if registration.wasNewInvitation then playerInvitationNotifications(registration.tournament, registration.player)
        else Vector.empty
      _ <- RecordBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
    yield TournamentSummaryView.fromDomain(registration.tournament)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def registerPlayer(
      context: ApiPlanContext,
      command: RegisterTournamentPlayerCommand
  ): IO[Option[PlayerInvitationResult]] =
    val connection = context.connection
    for
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${command.playerId.value} was not found")))
      _ <- RequirePermissionPrivateAPIMessage(command.actor, Permission.ManageTournamentStages, tournamentId = Some(command.tournamentId)).plan(context)
      result <- IO.blocking {
        ensurePlayerCanEnter(player, command)
        riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
          val wasNewInvitation =
            !tournament.whitelist.exists(_.playerId.contains(command.playerId)) &&
              !tournament.participatingPlayers.contains(command.playerId)
          val updatedTournament =
            riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, TournamentFunctions.registerPlayer(tournament, command.playerId))
          PlayerInvitationResult(updatedTournament, player, wasNewInvitation)
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
        notificationType = "TournamentPlayerInvited",
        title = "收到赛事邀请",
        body = "你被邀请参加赛事 " + tournament.name + "。",
        severity = Some("info"),
        sourceService = "tournament",
        sourceType = "tournament-player-invitation",
        sourceId = tournament.id.value,
        actionUrl = Some("/public/tournaments/" + tournament.id.value),
        objects = Map(
          "tournamentId" -> tournament.id.value,
          "playerId" -> player.id.value
        )
      )
    )

  private def ensurePlayerCanEnter(player: PlayerPrivateView, command: RegisterTournamentPlayerCommand): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"PlayerPrivateView ${command.playerId.value} cannot enter tournament ${command.tournamentId.value}")

  private final case class RegisterTournamentPlayerCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  )

  private final case class PlayerInvitationResult(
      tournament: Tournament,
      player: PlayerPrivateView,
      wasNewInvitation: Boolean
  )
