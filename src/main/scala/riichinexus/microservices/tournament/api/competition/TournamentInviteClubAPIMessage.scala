package riichinexus.microservices.tournament.api.competition
import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType, StructuredEventField}
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.club.api.audit.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.profile.`private`.ClubPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.objects.competition.TournamentMutationView
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.json.JsonCodecs.given
/** 邀请俱乐部参与赛事。 */
final case class TournamentInviteClubAPIMessage(
    tournamentId: String,
    clubId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView]:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- resolveOperatorActor(context)
      requestedTournamentId = TournamentId(tournamentId)
      requestedClubId = ClubId(clubId)
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.ManageTournamentStages, tournamentId = Some(requestedTournamentId)).plan(context)
      club <- resolveActiveClub(context, requestedClubId)
      invitation <- inviteClub(context, requestedTournamentId, requestedClubId, club)
        .map(_.getOrElse(throw NoSuchElementException(s"Tournament ${requestedTournamentId.value} was not found")))
      notificationRequests =
        if invitation._2 then clubInvitationNotifications(invitation._1, club)
        else Vector.empty
      _ <- RecordBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
      detail <- TournamentGetAPIMessage(requestedTournamentId.value).plan(context)
    yield TournamentMutationView(tournament = detail, scheduledTables = Vector.empty)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def resolveActiveClub(context: ApiPlanContext, clubId: ClubId): IO[ClubPrivateView] =
    ResolveClubReadModelsPrivateAPIMessage(Vector(clubId))
      .plan(context)
      .map(_.headOption.getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found")))
      .map { club =>
        if club.dissolvedAt.nonEmpty then
          throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")
        club
      }

  private def inviteClub(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      clubId: ClubId,
      club: ClubPrivateView
  ): IO[Option[(Tournament, Boolean)]] =
    IO.blocking {
      TournamentTable.findById(context.connection, tournamentId).map { tournament =>
        val wasNewInvitation =
          !tournament.whitelist.exists(_.clubId.contains(clubId)) &&
            !tournament.participatingClubs.contains(clubId)
        val updatedTournament =
          TournamentTable.save(
            context.connection,
            TournamentFunctions.whitelistClub(tournament, clubId)
          )
        updatedTournament -> wasNewInvitation
      }
    }

  private def clubInvitationNotifications(
      tournament: Tournament,
      club: ClubPrivateView
  ): Vector[CreateNotificationRequest] =
    (club.creator +: club.admins).distinct.map { recipient =>
      CreateNotificationRequest(
        recipientPlayerId = recipient.value,
        notificationType = NotificationType.TournamentClubInvited,
        title = "俱乐部收到赛事邀请",
        body = s"${club.name} 被邀请参加赛事 ${tournament.name}。",
        severity = Some(NotificationSeverity.Info),
        sourceService = NotificationSourceService.Tournament,
        sourceType = NotificationSourceType.TournamentClubInvitation,
        sourceId = tournament.id.value,
        actionUrl = Some(s"/public/tournaments/${tournament.id.value}"),
        objects = Map(
          StructuredEventField.toString(StructuredEventField.TournamentId) -> tournament.id.value,
          StructuredEventField.toString(StructuredEventField.ClubId) -> club.id.value
        )
      )
    }
