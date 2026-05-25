package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{Dashboard as DashboardResponse}
import upickle.default.*

final case class OpsAnalyticsPlayerDashboardAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId
) extends APIMessage[DashboardResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DashboardResponse] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ViewOwnDashboard, subjectPlayerId = Some(playerId))
      context.support.opsAnalyticsModule.tables.findDashboard(DashboardOwner.Player(playerId))
        .map(DashboardResponse.fromDomain)
        .getOrElse(throw NoSuchElementException(s"Dashboard for player ${playerId.value} was not found"))
    }
