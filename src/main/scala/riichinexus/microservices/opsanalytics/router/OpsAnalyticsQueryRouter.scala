package riichinexus.microservices.opsanalytics.router

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.api.{AuditTrailApi, DashboardApi}
import riichinexus.microservices.opsanalytics.objects.AuditTrailQuery
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables
import riichinexus.api.http.RouteSupport

object OpsAnalyticsQueryRouter:
  private final case class Dependencies(tables: OpsAnalyticsTables)

  private def dependencies(support: RouteSupport): Dependencies =
    Dependencies(tables = support.opsAnalyticsModule.tables)

  def dashboardRoutes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ GET -> Root / "dashboards" / "players" / playerId =>
      support.handled {
        val targetPlayerId = PlayerId(playerId)
        val operator = support.queryPrincipal(req)
        support.requirePermission(
          principal = operator,
          permission = Permission.ViewOwnDashboard,
          subjectPlayerId = Some(targetPlayerId)
        )
        support.optionJsonResponse(DashboardApi.playerDashboard(deps.tables, targetPlayerId))
      }

    case req @ GET -> Root / "dashboards" / "clubs" / clubId =>
      support.handled {
        val targetClubId = ClubId(clubId)
        val operator = support.queryPrincipal(req)
        support.requirePermission(
          principal = operator,
          permission = Permission.ViewClubDashboard,
          clubId = Some(targetClubId)
        )
        support.optionJsonResponse(DashboardApi.clubDashboard(deps.tables, targetClubId))
      }

    case req @ GET -> Root / "advanced-stats" / "players" / playerId =>
      support.handled {
        val targetPlayerId = PlayerId(playerId)
        val operator = support.queryPrincipal(req)
        support.requirePermission(
          principal = operator,
          permission = Permission.ViewOwnDashboard,
          subjectPlayerId = Some(targetPlayerId)
        )
        support.optionJsonResponse(DashboardApi.playerAdvancedStats(deps.tables, targetPlayerId))
      }

    case req @ GET -> Root / "advanced-stats" / "clubs" / clubId =>
      support.handled {
        val targetClubId = ClubId(clubId)
        val operator = support.queryPrincipal(req)
        support.requirePermission(
          principal = operator,
          permission = Permission.ViewClubDashboard,
          clubId = Some(targetClubId)
        )
        support.optionJsonResponse(DashboardApi.clubAdvancedStats(deps.tables, targetClubId))
      }
  }

  def auditRoutes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ GET -> Root / "audits" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ViewAuditTrail)
        val query = AuditTrailQuery(
          aggregateType = support.queryParam(req, "aggregateType").filter(_.nonEmpty),
          aggregateId = support.queryParam(req, "aggregateId").filter(_.nonEmpty),
          actorId = support.queryParam(req, "actorId").filter(_.nonEmpty).map(PlayerId(_)),
          eventType = support.queryParam(req, "eventType").filter(_.nonEmpty)
        )
        val audits = AuditTrailApi.listAudits(deps.tables, query)
        support.pagedJsonResponse(
          req,
          audits,
          support.activeFilters(req, "aggregateType", "aggregateId", "actorId", "eventType", "operatorId")
        )
      }

    case req @ GET -> Root / "audits" / aggregateType / aggregateId =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ViewAuditTrail)
        val query = AuditTrailQuery(
          actorId = support.queryParam(req, "actorId").filter(_.nonEmpty).map(PlayerId(_)),
          eventType = support.queryParam(req, "eventType").filter(_.nonEmpty)
        )
        val audits = AuditTrailApi.listAuditsByAggregate(deps.tables, aggregateType, aggregateId, query)
        support.pagedJsonResponse(
          req,
          audits,
          support.activeFilters(req, "actorId", "eventType", "operatorId") ++
            Map("aggregateType" -> aggregateType, "aggregateId" -> aggregateId)
        )
      }
  }

