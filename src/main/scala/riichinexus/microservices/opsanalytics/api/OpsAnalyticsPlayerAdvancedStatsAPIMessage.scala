package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class OpsAnalyticsPlayerAdvancedStatsAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId
) extends APIMessage[AdvancedStatsBoard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ViewOwnDashboard, subjectPlayerId = Some(playerId))
      context.support.opsAnalyticsModule.tables.findAdvancedStatsBoard(DashboardOwner.Player(playerId))
        .getOrElse(throw NoSuchElementException(s"Advanced stats board for player ${playerId.value} was not found"))
    }
