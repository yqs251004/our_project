package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TournamentStageTableScheduler
import riichinexus.microservices.tournament.objects.stage.table.apiTypes.TournamentTableView
import riichinexus.microservices.tournament.objects.competition.apiTypes.TournamentMutationView
import upickle.default.ReadWriter

/** 为赛事阶段生成牌桌安排。 */
final case class TournamentStageScheduleTablesAPIMessage(
    tournamentId: String,
    stageId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- resolveOperatorActor(context)
      command = ScheduleStageTablesCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = actor
      )
      scheduledTables <- scheduleTables(context, command)
      detail <- TournamentGetAPIMessage(command.tournamentId.value).plan(context)
    yield TournamentMutationView(
      tournament = detail,
      scheduledTables = scheduledTables
        .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
        .map(TournamentTableView.fromDomain)
    )

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def scheduleTables(
      context: ApiPlanContext,
      command: ScheduleStageTablesCommand
  ): IO[Vector[Table]] =
    for
      _ <- RequirePermissionPrivateAPIMessage(
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(command.tournamentId)
      ).plan(context)
      tables <- TournamentStageTableScheduler.schedule(
        connection = context.connection,
        tournamentId = command.tournamentId,
        stageId = command.stageId
      )
    yield tables

  private final case class ScheduleStageTablesCommand(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipalPrivateView
  )

