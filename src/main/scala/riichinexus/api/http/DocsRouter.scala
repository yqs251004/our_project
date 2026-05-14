package riichinexus.api.http

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.microservices.shared.api.docs.OpenApiSupport
import riichinexus.microservices.shared.api.responses.HealthResponse

object DocsRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "openapi.json" =>
      support.handled(support.textResponse(Status.Ok, support.openApiJson(req), "application/json; charset=utf-8"))

    case GET -> Root / "swagger" =>
      support.handled(support.textResponse(Status.Ok, OpenApiSupport.swaggerHtml(), "text/html; charset=utf-8"))

    case GET -> Root =>
      support.handled(
        support.jsonResponse(
          Status.Ok,
          HealthResponse(
            status = "ok",
            storage = support.storageLabel,
            timestamp = java.time.Instant.now()
          )
        )
      )

    case GET -> Root / "health" =>
      support.handled(
        support.jsonResponse(
          Status.Ok,
          HealthResponse(
            status = "ok",
            storage = support.storageLabel,
            timestamp = java.time.Instant.now()
          )
        )
      )
  }
