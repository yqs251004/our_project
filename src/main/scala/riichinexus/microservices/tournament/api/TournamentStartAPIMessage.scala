package riichinexus.microservices.tournament.api
import riichinexus.microservices.tournament.domain.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.apiTypes.TournamentSummaryView

/** 开始赛事。 */
final case class TournamentStartAPIMessage(tournamentId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView]:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- resolveOperatorActor(context)
      command = StartTournamentCommand(TournamentId(tournamentId), actor)
      _ <- RequirePermissionPrivateAPIMessage(command.actor, Permission.ManageTournamentStages, tournamentId = Some(command.tournamentId)).plan(context)
      tournament <- IO.blocking {
        {
          startTournament(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentViewFunctions.tournamentSummaryView(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def startTournament(
      connection: java.sql.Connection,
      command: StartTournamentCommand
  ): Option[Tournament] =
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      ensureTournamentHasParticipants(tournament, command.tournamentId)
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, TournamentFunctions.start(tournament))
    }

  private def ensureTournamentHasParticipants(tournament: Tournament, tournamentId: TournamentId): Unit =
    if tournament.participatingPlayers.isEmpty && tournament.participatingClubs.isEmpty then
      throw IllegalArgumentException(
        s"Tournament ${tournamentId.value} cannot start without participants"
      )

  /** 启动赛事前完成授权和参赛校验后的内部命令。 */
  private final case class StartTournamentCommand(
      tournamentId: TournamentId,
      actor: AccessPrincipalPrivateView
  )
