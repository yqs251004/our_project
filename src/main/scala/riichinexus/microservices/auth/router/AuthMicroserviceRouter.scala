package riichinexus.microservices.auth.router

import java.time.Instant

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.api.{AccountApi, AuthApplicationService, GuestSessionApi, GuestSessionApplicationService, SessionApi}
import riichinexus.microservices.auth.api.requests.*
import riichinexus.microservices.auth.api.responses.AuthResponses.given
import riichinexus.microservices.auth.objects.{CurrentSessionQuery, GuestSessionListQuery}
import riichinexus.microservices.auth.tables.AuthTables
import riichinexus.api.http.RouteSupport

object AuthMicroserviceRouter:
  private final case class Dependencies(
      tables: AuthTables,
      authService: AuthApplicationService,
      guestSessionService: GuestSessionApplicationService
  )

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.authModule
    Dependencies(
      tables = module.tables,
      authService = module.authService,
      guestSessionService = module.guestSessionService
    )

  def authRoutes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ POST -> Root / "auth" / "register" =>
      support.handled {
        support.readJsonBody[RegisterAccountRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, AccountApi.register(deps.authService, request, Instant.now()))
        }
      }

    case req @ POST -> Root / "auth" / "login" =>
      support.handled {
        support.readJsonBody[LoginRequest](req).flatMap { request =>
          support.jsonResponse(Status.Ok, AccountApi.login(deps.authService, request, Instant.now()))
        }
      }

    case req @ GET -> Root / "auth" / "session" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          SessionApi.restoreSession(
            service = deps.authService,
            token = support.bearerToken(req).getOrElse(""),
            asOf = Instant.now()
          )
        )
      }

    case req @ POST -> Root / "auth" / "logout" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          SessionApi.logout(
            service = deps.authService,
            token = support.bearerToken(req).getOrElse(""),
            loggedOutAt = Instant.now()
          )
        )
      }
  }

  def publicRoutes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ GET -> Root / "session" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          SessionApi.currentSessionView(
            tables = deps.tables,
            guestSessionService = deps.guestSessionService,
            query = CurrentSessionQuery(
              operatorId = support.queryParam(req, "operatorId").filter(_.nonEmpty).map(PlayerId(_)),
              guestSessionId = support.queryParam(req, "guestSessionId").filter(_.nonEmpty).map(GuestSessionId(_))
            )
          )
        )
      }

    case req @ GET -> Root / "guest-sessions" =>
      support.handled {
        val sessions = GuestSessionApi.listSessions(
          tables = deps.tables,
          query = GuestSessionListQuery(
            activeOnly = support.queryBooleanParam(req, "activeOnly"),
            asOf = Instant.now()
          )
        )
        support.pagedJsonResponse(req, sessions, support.activeFilters(req, "activeOnly"))
      }

    case req @ POST -> Root / "guest-sessions" =>
      support.handled {
        support.readOptionalJsonBody[CreateGuestSessionRequest](req).flatMap { request =>
          support.jsonResponse(Status.Created, GuestSessionApi.createSession(deps.guestSessionService, request))
        }
      }

    case GET -> Root / "guest-sessions" / sessionId =>
      support.handled {
        support.optionJsonResponse(GuestSessionApi.findSession(deps.tables, GuestSessionId(sessionId)))
      }

    case req @ POST -> Root / "guest-sessions" / sessionId / "revoke" =>
      support.handled {
        support.readOptionalJsonBody[RevokeGuestSessionRequest](req).flatMap { request =>
          support.optionJsonResponse(
            GuestSessionApi.revokeSession(
              service = deps.guestSessionService,
              sessionId = GuestSessionId(sessionId),
              request = request
            )
          )
        }
      }

    case req @ POST -> Root / "guest-sessions" / sessionId / "upgrade" =>
      support.handled {
        support.readJsonBody[UpgradeGuestSessionRequest](req).flatMap { request =>
          support.optionJsonResponse(
            GuestSessionApi.upgradeSession(
              service = deps.guestSessionService,
              sessionId = GuestSessionId(sessionId),
              request = request
            )
          )
        }
      }
  }

