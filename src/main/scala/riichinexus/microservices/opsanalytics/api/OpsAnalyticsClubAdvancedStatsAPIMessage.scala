package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class OpsAnalyticsClubAdvancedStatsAPIMessage(
    clubId: ClubId,
    operatorId: PlayerId
) extends APIMessage[AdvancedStatsBoard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ViewClubDashboard, clubId = Some(clubId))
      context.support.opsAnalyticsModule.tables.findAdvancedStatsBoard(DashboardOwner.Club(clubId))
        .getOrElse(throw NoSuchElementException(s"Advanced stats board for club ${clubId.value} was not found"))
    }
