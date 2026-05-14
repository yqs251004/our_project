package riichinexus.microservices.player.router

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.api.{PlayerApplicationService, PlayerManagementApi, PlayerQueryApi}
import riichinexus.microservices.player.api.requests.PlayerRequests.given
import riichinexus.microservices.player.api.requests.*
import riichinexus.microservices.player.objects.PlayerListQuery
import riichinexus.microservices.player.tables.PlayerTables
import riichinexus.api.http.RouteSupport

object PlayerMicroserviceRouter:
  private final case class Dependencies(tables: PlayerTables, service: PlayerApplicationService)

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.playerModule
    Dependencies(
      tables = module.tables,
      service = module.service
    )

  def routes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ GET -> Root / "players" =>
      support.handled {
        val query = PlayerListQuery(
          clubId = support.queryParam(req, "clubId").filter(_.nonEmpty).map(ClubId(_)),
          status = support.queryParam(req, "status").filter(_.nonEmpty).map(
            support.parseEnum("status", _)(PlayerStatus.valueOf)
          ),
          nickname = support.queryParam(req, "nickname").filter(_.nonEmpty)
        )
        val players = PlayerQueryApi.listPlayers(deps.tables, query, support.containsIgnoreCase)
        support.pagedJsonResponse(req, players, support.activeFilters(req, "clubId", "status", "nickname"))
      }

    case req @ GET -> Root / "players" / "me" =>
      support.handled {
        val operatorId = support.queryParam(req, "operatorId")
          .filter(_.nonEmpty)
          .map(PlayerId(_))
          .getOrElse(throw IllegalArgumentException("Query parameter operatorId is required"))
        support.optionJsonResponse(PlayerQueryApi.findPlayer(deps.tables, operatorId))
      }

    case GET -> Root / "players" / playerId =>
      support.handled {
        support.optionJsonResponse(PlayerQueryApi.findPlayer(deps.tables, PlayerId(playerId)))
      }

    case req @ POST -> Root / "players" =>
      support.handled {
        support.readJsonBody[CreatePlayerRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, PlayerManagementApi.createPlayer(deps.service, request))
        }
      }
  }

