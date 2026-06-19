package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
/** 获取俱乐部运营仪表盘。 */
final case class OpsAnalyticsClubDashboardAPIMessage(
    clubId: ClubId,
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
      permission = Permission.ViewClubDashboard,
      clubId = Some(clubId.value)
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to view dashboard for club ${clubId.value}"))
    }

  private def findDashboard(context: ApiPlanContext): Dashboard =
    DashboardTable
      .findByOwner(context.connection, DashboardOwner.Club(clubId))
      .getOrElse(throw NoSuchElementException(s"Dashboard for club ${clubId.value} was not found"))
