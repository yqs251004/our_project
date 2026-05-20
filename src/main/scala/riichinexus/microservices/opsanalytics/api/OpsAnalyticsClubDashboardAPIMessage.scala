package riichinexus.microservices.opsanalytics.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class OpsAnalyticsClubDashboardAPIMessage(
    clubId: ClubId,
    operatorId: PlayerId
) extends APIMessage[Dashboard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ViewClubDashboard, clubId = Some(clubId))
      context.support.opsAnalyticsModule.tables.findDashboard(DashboardOwner.Club(clubId))
        .getOrElse(throw NoSuchElementException(s"Dashboard for club ${clubId.value} was not found"))
    }
