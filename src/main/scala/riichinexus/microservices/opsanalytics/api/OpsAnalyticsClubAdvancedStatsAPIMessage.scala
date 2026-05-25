package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{AdvancedStatsBoard as AdvancedStatsBoardResponse}
import upickle.default.*

final case class OpsAnalyticsClubAdvancedStatsAPIMessage(
    clubId: ClubId,
    operatorId: PlayerId
) extends APIMessage[AdvancedStatsBoardResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoardResponse] =
    for
      operator <- IO(context.support.principal(operatorId))
      _ <- IO(requireDashboardPermission(context, operator))
      board <- IO(findAdvancedStatsBoard(context))
    yield AdvancedStatsBoardResponse.fromDomain(board)

  private def requireDashboardPermission(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ViewClubDashboard, clubId = Some(clubId))

  private def findAdvancedStatsBoard(context: ApiPlanContext): AdvancedStatsBoard =
    context.support.opsAnalyticsModule.tables
      .findAdvancedStatsBoard(DashboardOwner.Club(clubId))
      .getOrElse(throw NoSuchElementException(s"Advanced stats board for club ${clubId.value} was not found"))
