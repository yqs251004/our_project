package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import upickle.default.*

final case class OpsAnalyticsPlayerAdvancedStatsAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId
) extends APIMessage[AdvancedStatsBoard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    for
      operator <- IO(context.principal(operatorId))
      _ <- IO(requireDashboardPermission(context, operator))
      board <- IO(findAdvancedStatsBoard(context))
    yield board

  private def requireDashboardPermission(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ViewOwnDashboard, subjectPlayerId = Some(playerId))

  private def findAdvancedStatsBoard(context: ApiPlanContext): AdvancedStatsBoard =
    AdvancedStatsBoardTable
      .findByOwner(context.connection, DashboardOwner.Player(playerId))
      .getOrElse(throw NoSuchElementException(s"Advanced stats board for player ${playerId.value} was not found"))
