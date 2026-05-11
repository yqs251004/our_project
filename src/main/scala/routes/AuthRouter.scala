package routes

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.api.*
import riichinexus.api.ApiModels.given
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given

object AuthRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "auth" / "register" =>
      support.handled {
        support.readJsonBody[RegisterAccountRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Created,
            support.app.authService.register(
              username = request.username,
              password = request.password,
              displayName = request.displayName
            )
          )
        }
      }

    case req @ POST -> Root / "auth" / "login" =>
      support.handled {
        support.readJsonBody[LoginRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            support.app.authService.login(
              username = request.username,
              password = request.password
            )
          )
        }
      }

    case req @ GET -> Root / "auth" / "session" =>
      support.handled {
        support.jsonResponse(
          Status.Ok,
          support.app.authService.restoreSession(
            support.bearerToken(req).getOrElse("")
          )
        )
      }

    case req @ POST -> Root / "auth" / "logout" =>
      support.handled {
        support.bearerToken(req).foreach(token => support.app.authService.logout(token))
        support.jsonResponse(Status.Ok, ApiMessage("Logged out"))
      }
  }
