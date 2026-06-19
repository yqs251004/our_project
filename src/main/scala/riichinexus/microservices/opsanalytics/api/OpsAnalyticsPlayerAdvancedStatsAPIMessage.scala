package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
/** 获取玩家高级统计面板。 */
final case class OpsAnalyticsPlayerAdvancedStatsAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId
) extends APIMessage[AdvancedStatsBoard]:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    for
      operator <- ResolveAccessPrincipalPrivateAPIMessage(operatorId).plan(context)
      _ <- requireDashboardPermission(context, operator)
      board <- IO.blocking(findAdvancedStatsBoard(context))
    yield board

  private def requireDashboardPermission(context: ApiPlanContext, operator: AccessPrincipalPrivateView): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      operatorId = operator.playerId.map(_.value),
      permission = Permission.ViewOwnDashboard,
      subjectPlayerId = Some(playerId.value)
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to view advanced stats for player ${playerId.value}"))
    }

  private def findAdvancedStatsBoard(context: ApiPlanContext): AdvancedStatsBoard =
    AdvancedStatsBoardTable
      .findByOwner(context.connection, DashboardOwner.Player(playerId))
      .getOrElse(throw NoSuchElementException(s"Advanced stats board for player ${playerId.value} was not found"))
