package routes

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given

object DashboardRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "dashboards" / "players" / playerId =>
      support.handled {
        val targetPlayerId = PlayerId(playerId)
        val operator = support.queryPrincipal(req)
        support.requirePermission(
          principal = operator,
          permission = Permission.ViewOwnDashboard,
          subjectPlayerId = Some(targetPlayerId)
        )
        support.optionJsonResponse(support.app.dashboardRepository.findByOwner(DashboardOwner.Player(targetPlayerId)))
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
        support.optionJsonResponse(support.app.dashboardRepository.findByOwner(DashboardOwner.Club(targetClubId)))
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
        support.optionJsonResponse(
          support.app.advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(targetPlayerId))
        )
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
        support.optionJsonResponse(
          support.app.advancedStatsBoardRepository.findByOwner(DashboardOwner.Club(targetClubId))
        )
      }
  }
