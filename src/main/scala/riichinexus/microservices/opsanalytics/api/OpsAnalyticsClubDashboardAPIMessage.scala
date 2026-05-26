package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import upickle.default.*

final case class OpsAnalyticsClubDashboardAPIMessage(
    clubId: ClubId,
    operatorId: PlayerId
) extends APIMessage[Dashboard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    for
      operator <- IO(context.principal(operatorId))
      _ <- IO(requireDashboardPermission(context, operator))
      dashboard <- IO(findDashboard(context))
    yield dashboard

  private def requireDashboardPermission(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ViewClubDashboard, clubId = Some(clubId))

  private def findDashboard(context: ApiPlanContext): Dashboard =
    DashboardTable
      .findByOwner(context.connection, DashboardOwner.Club(clubId))
      .getOrElse(throw NoSuchElementException(s"Dashboard for club ${clubId.value} was not found"))
