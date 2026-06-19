package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.TournamentMutationView

import upickle.default.ReadWriter

/** 发布赛事供前端公开查看。 */
final case class TournamentPublishAPIMessage(tournamentId: String, operatorId: Option[String] = None) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- resolveOperatorActor(context)
      command = PublishTournamentCommand(TournamentId(tournamentId), actor)
      _ <- RequirePermissionPrivateAPIMessage(command.actor, Permission.ManageTournamentStages, tournamentId = Some(command.tournamentId)).plan(context)
      _ <- IO.blocking {
        {
          publishTournament(context.connection, command)
        }
      }
      detail <- TournamentGetAPIMessage(command.tournamentId.value).plan(context)
    yield TournamentMutationView(tournament = detail, scheduledTables = Vector.empty)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def publishTournament(
      connection: java.sql.Connection,
      command: PublishTournamentCommand
  ): Unit =
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId).foreach { tournament =>
      ensureTournamentHasStages(tournament, command.tournamentId)
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, TournamentFunctions.publish(tournament))
    }

  private def ensureTournamentHasStages(tournament: Tournament, tournamentId: TournamentId): Unit =
    if tournament.stages.isEmpty then
      throw IllegalArgumentException(
        s"Tournament ${tournamentId.value} cannot be published without stages"
      )

  private final case class PublishTournamentCommand(
      tournamentId: TournamentId,
      actor: AccessPrincipalPrivateView
  )

