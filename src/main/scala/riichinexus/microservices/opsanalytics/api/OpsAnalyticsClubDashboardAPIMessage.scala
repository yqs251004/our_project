package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
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
      operator <- IO.blocking(AuthAccessPrincipalResolver.principal(context, operatorId))
      _ <- IO.blocking(requireDashboardPermission(context, operator))
      dashboard <- IO.blocking(findDashboard(context))
    yield dashboard

  private def requireDashboardPermission(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions.requirePermission(context.support.authorizationService, operator, Permission.ViewClubDashboard, clubId = Some(clubId))

  private def findDashboard(context: ApiPlanContext): Dashboard =
    DashboardTable
      .findByOwner(context.connection, DashboardOwner.Club(clubId))
      .getOrElse(throw NoSuchElementException(s"Dashboard for club ${clubId.value} was not found"))
