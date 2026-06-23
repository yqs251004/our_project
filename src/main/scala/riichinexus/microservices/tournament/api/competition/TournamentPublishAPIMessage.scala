package riichinexus.microservices.tournament.api.competition
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.TournamentMutationView

/** 发布赛事供前端公开查看。 */
final case class TournamentPublishAPIMessage(tournamentId: String, operatorId: Option[String] = None) extends APIMessage[TournamentMutationView]:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- resolveOperatorActor(context)
      requestedTournamentId = TournamentId(tournamentId)
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.ManageTournamentStages, tournamentId = Some(requestedTournamentId)).plan(context)
      _ <- IO.blocking(publishTournament(context.connection, requestedTournamentId))
      detail <- TournamentGetAPIMessage(requestedTournamentId.value).plan(context)
    yield TournamentMutationView(tournament = detail, scheduledTables = Vector.empty)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def publishTournament(
      connection: java.sql.Connection,
      tournamentId: TournamentId
  ): Unit =
    TournamentTable.findById(connection, tournamentId).foreach { tournament =>
      ensureTournamentHasStages(tournament, tournamentId)
      TournamentTable.save(connection, TournamentFunctions.publish(tournament))
    }

  private def ensureTournamentHasStages(tournament: Tournament, tournamentId: TournamentId): Unit =
    if tournament.stages.isEmpty then
      throw IllegalArgumentException(
        s"Tournament ${tournamentId.value} cannot be published without stages"
      )

