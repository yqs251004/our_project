package riichinexus.microservices.tournament.api.competition
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.club.api.audit.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.TournamentMutationView

/** 移除俱乐部在赛事中的邀请或参与记录。 */
final case class TournamentRemoveClubParticipationAPIMessage(tournamentId: String, clubId: String, operatorId: Option[String] = None) extends APIMessage[TournamentMutationView]:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- resolveOperatorActor(context)
      requestedTournamentId = TournamentId(tournamentId)
      requestedClubId = ClubId(clubId)
      _ <- removeClubParticipation(context, requestedTournamentId, requestedClubId, actor)
      detail <- TournamentGetAPIMessage(requestedTournamentId.value).plan(context)
    yield TournamentMutationView(tournament = detail, scheduledTables = Vector.empty)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def removeClubParticipation(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipalPrivateView
  ): IO[Unit] =
    for
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.ManageTournamentStages, tournamentId = Some(tournamentId)).plan(context)
      _ <- ResolveClubReadModelsPrivateAPIMessage(Vector(clubId))
        .plan(context)
        .map(_.headOption.getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found")))
      _ <- IO.blocking {
        TournamentTable.findById(context.connection, tournamentId).foreach { tournament =>
          ensureClubTracked(tournament, tournamentId, clubId)
          TournamentTable.save(context.connection, TournamentFunctions.removeClub(tournament, clubId))
        }
      }
    yield ()

  private def ensureClubTracked(
      tournament: Tournament,
      tournamentId: TournamentId,
      clubId: ClubId
  ): Unit =
    val trackedParticipation =
      tournament.participatingClubs.contains(clubId) ||
        tournament.whitelist.exists(_.clubId.contains(clubId))
    if !trackedParticipation then
      throw IllegalArgumentException(
        s"Club ${clubId.value} is not participating in tournament ${tournamentId.value}"
      )
