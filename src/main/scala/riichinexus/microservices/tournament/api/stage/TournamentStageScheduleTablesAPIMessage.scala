package riichinexus.microservices.tournament.api.stage
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.tournament.api.competition.TournamentGetAPIMessage
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TournamentStageTableScheduler
import riichinexus.microservices.tournament.objects.stage.table.TournamentTableView
import riichinexus.microservices.tournament.objects.competition.TournamentMutationView
/** 为赛事阶段生成牌桌安排。 */
final case class TournamentStageScheduleTablesAPIMessage(
    tournamentId: String,
    stageId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView]:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- resolveOperatorActor(context)
      requestedTournamentId = TournamentId(tournamentId)
      requestedStageId = TournamentStageId(stageId)
      scheduledTables <- scheduleTables(context, requestedTournamentId, requestedStageId, actor)
      detail <- TournamentGetAPIMessage(requestedTournamentId.value).plan(context)
    yield TournamentMutationView(
      tournament = detail,
      scheduledTables = scheduledTables
        .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
        .map(TournamentViewFunctions.tableView)
    )

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty).map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def scheduleTables(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipalPrivateView
  ): IO[Vector[Table]] =
    for
      _ <- RequirePermissionPrivateAPIMessage(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      ).plan(context)
      tables <- TournamentStageTableScheduler.schedule(
        connection = context.connection,
        tournamentId = tournamentId,
        stageId = stageId
      )
    yield tables
