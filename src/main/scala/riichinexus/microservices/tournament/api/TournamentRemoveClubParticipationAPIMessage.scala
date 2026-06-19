package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.club.api.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.apiTypes.TournamentMutationView

import upickle.default.ReadWriter

/** 移除俱乐部在赛事中的邀请或参与记录。 */
final case class TournamentRemoveClubParticipationAPIMessage(tournamentId: String, clubId: String, operatorId: Option[String] = None) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- resolveOperatorActor(context)
      command = RemoveClubParticipationCommand(TournamentId(tournamentId), ClubId(clubId), actor)
      _ <- removeClubParticipation(context, command)
      detail <- TournamentGetAPIMessage(command.tournamentId.value).plan(context)
    yield TournamentMutationView(tournament = detail, scheduledTables = Vector.empty)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def removeClubParticipation(
      context: ApiPlanContext,
      command: RemoveClubParticipationCommand
  ): IO[Unit] =
    for
      _ <- RequirePermissionPrivateAPIMessage(command.actor, Permission.ManageTournamentStages, tournamentId = Some(command.tournamentId)).plan(context)
      _ <- ResolveClubReadModelsPrivateAPIMessage(Vector(command.clubId))
        .plan(context)
        .map(_.headOption.getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found")))
      _ <- IO.blocking {
        riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(context.connection, command.tournamentId).foreach { tournament =>
          ensureClubTracked(tournament, command)
          riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(context.connection, TournamentFunctions.removeClub(tournament, command.clubId))
        }
      }
    yield ()

  private def ensureClubTracked(
      tournament: Tournament,
      command: RemoveClubParticipationCommand
  ): Unit =
    val trackedParticipation =
      tournament.participatingClubs.contains(command.clubId) ||
        tournament.whitelist.exists(_.clubId.contains(command.clubId))
    if !trackedParticipation then
      throw IllegalArgumentException(
        s"Club ${command.clubId.value} is not participating in tournament ${command.tournamentId.value}"
      )

  private final case class RemoveClubParticipationCommand(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipalPrivateView
  )
