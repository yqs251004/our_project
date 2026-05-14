package riichinexus.microservices.platformadmin.router

import java.time.Instant

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.api.{ClubGovernanceApi, PlatformRoleApi, PlayerModerationApi, SuperAdminService}
import riichinexus.microservices.platformadmin.api.requests.*
import riichinexus.microservices.platformadmin.tables.PlatformAdminTables
import riichinexus.api.http.RouteSupport

object PlatformAdminRouter:
  private final case class Dependencies(tables: PlatformAdminTables, service: SuperAdminService)

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.platformAdminModule
    Dependencies(
      tables = module.tables,
      service = module.service
    )

  def routes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ POST -> Root / "admin" / "players" / playerId / "ban" =>
      support.handled {
        support.readJsonBody[BanPlayerRequest](req).flatMap { request =>
          support.optionJsonResponse(
            PlayerModerationApi.banPlayer(
              tables = deps.tables,
              service = deps.service,
              playerId = PlayerId(playerId),
              actor = support.principal(request.operator),
              request = request,
              at = Instant.now()
            )
          )
        }
      }

    case req @ POST -> Root / "admin" / "clubs" / clubId / "dissolve" =>
      support.handled {
        support.readJsonBody[DissolveClubRequest](req).flatMap { request =>
          support.optionJsonResponse(
            ClubGovernanceApi.dissolveClub(
              tables = deps.tables,
              service = deps.service,
              clubId = ClubId(clubId),
              actor = support.principal(request.operator),
              request = request,
              at = Instant.now()
            )
          )
        }
      }

    case req @ POST -> Root / "admin" / "players" / playerId / "super-admin" =>
      support.handled {
        support.readJsonBody[GrantSuperAdminRequest](req).flatMap { request =>
          support.optionJsonResponse(
            PlatformRoleApi.grantSuperAdmin(
              tables = deps.tables,
              service = deps.service,
              playerId = PlayerId(playerId),
              actor = support.principal(request.operator),
              request = request,
              grantedAt = Instant.now()
            )
          )
        }
      }
  }

