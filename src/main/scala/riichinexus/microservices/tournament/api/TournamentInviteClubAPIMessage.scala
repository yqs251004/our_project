package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage, ResolveSystemAccessPrincipalPrivateAPIMessage}

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.api.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.`private`.ClubPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.objects.competition.apiTypes.TournamentMutationView
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 邀请俱乐部参与赛事。 */
final case class TournamentInviteClubAPIMessage(
    tournamentId: String,
    clubId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- resolveOperatorActor(context)
      command = InviteClubCommand(TournamentId(tournamentId), ClubId(clubId), actor)
      _ <- RequirePermissionPrivateAPIMessage(command.actor, Permission.ManageTournamentStages, tournamentId = Some(command.tournamentId)).plan(context)
      club <- resolveActiveClub(context, command.clubId)
      invitation <- inviteClub(context, command, club)
        .map(_.getOrElse(throw NoSuchElementException(s"Tournament ${command.tournamentId.value} was not found")))
      notificationRequests =
        if invitation.wasNewInvitation then clubInvitationNotifications(invitation.tournament, invitation.club)
        else Vector.empty
      _ <- RecordBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
      detail <- TournamentGetAPIMessage(command.tournamentId.value).plan(context)
    yield TournamentMutationView(tournament = detail, scheduledTables = Vector.empty)

  private def resolveOperatorActor(context: ApiPlanContext): IO[riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView] =
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
      command: InviteClubCommand,
      club: ClubPrivateView
  ): IO[Option[ClubInvitationResult]] =
    IO.blocking {
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(context.connection, command.tournamentId).map { tournament =>
        val wasNewInvitation =
          !tournament.whitelist.exists(_.clubId.contains(command.clubId)) &&
            !tournament.participatingClubs.contains(command.clubId)
        val updatedTournament =
          riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(
            context.connection,
            TournamentFunctions.whitelistClub(tournament, command.clubId)
          )
        ClubInvitationResult(updatedTournament, club, wasNewInvitation)
      }
    }

  private def clubInvitationNotifications(
      tournament: Tournament,
      club: ClubPrivateView
  ): Vector[CreateNotificationRequest] =
    (club.creator +: club.admins).distinct.map { recipient =>
      CreateNotificationRequest(
        recipientPlayerId = recipient.value,
        notificationType = "TournamentClubInvited",
        title = "俱乐部收到赛事邀请",
        body = s"${club.name} 被邀请参加赛事 ${tournament.name}。",
        severity = Some("info"),
        sourceService = "tournament",
        sourceType = "tournament-club-invitation",
        sourceId = tournament.id.value,
        actionUrl = Some(s"/public/tournaments/${tournament.id.value}"),
        objects = Map(
          "tournamentId" -> tournament.id.value,
          "clubId" -> club.id.value
        )
      )
    }

  private final case class InviteClubCommand(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
  )

  private final case class ClubInvitationResult(
      tournament: Tournament,
      club: ClubPrivateView,
      wasNewInvitation: Boolean
  )
