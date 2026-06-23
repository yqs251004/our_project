package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.AuthCheckPermissionAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
/** 获取玩家运营仪表盘。 */
final case class OpsAnalyticsPlayerDashboardAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId
) extends APIMessage[Dashboard]:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    for
      operator <- ResolveAccessPrincipalPrivateAPIMessage(operatorId).plan(context)
      _ <- requireDashboardPermission(context, operator)
      dashboard <- IO.blocking(findDashboard(context))
    yield dashboard

  private def requireDashboardPermission(context: ApiPlanContext, operator: AccessPrincipalPrivateView): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      operatorId = operator.playerId.map(_.value),
      permission = Permission.ViewOwnDashboard,
      subjectPlayerId = Some(playerId.value)
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to view dashboard for player ${playerId.value}"))
    }

  private def findDashboard(context: ApiPlanContext): Dashboard =
    DashboardTable
      .findByOwner(context.connection, DashboardOwner.Player(playerId))
      .getOrElse(throw NoSuchElementException(s"Dashboard for player ${playerId.value} was not found"))
