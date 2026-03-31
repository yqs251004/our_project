package routes

import api.OpenApiSupport
import api.contracts.JsonSupport.given
import cats.effect.IO
import model.DomainModels.*
import objects.HealthResponse
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*

object DocsRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "openapi.json" =>
      support.handled(support.textResponse(Status.Ok, support.openApiJson(req), "application/json; charset=utf-8"))

    case GET -> Root / "swagger" =>
      support.handled(support.textResponse(Status.Ok, OpenApiSupport.swaggerHtml("/openapi.json"), "text/html; charset=utf-8"))

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

    case req @ GET -> Root / "demo" / "summary" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val bootstrapIfMissing = support.queryBooleanParam(req, "bootstrapIfMissing").getOrElse(false)
        val refreshDerived = support.queryBooleanParam(req, "refreshDerived").getOrElse(true)
        val summary = support.app.demoScenarioService.currentScenario(variant = variant, refreshDerived = refreshDerived)
          .orElse {
            if bootstrapIfMissing then Some(support.app.demoScenarioService.bootstrapScenario(variant = variant, refreshDerived = refreshDerived))
            else None
          }
        support.optionJsonResponse(summary)
      }

    case req @ GET -> Root / "demo" / "readiness" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val bootstrapIfMissing = support.queryBooleanParam(req, "bootstrapIfMissing").getOrElse(false)
        val refreshDerived = support.queryBooleanParam(req, "refreshDerived").getOrElse(true)
        support.optionJsonResponse(
          support.app.demoScenarioService.currentReadiness(
            variant = variant,
            bootstrapIfMissing = bootstrapIfMissing,
            refreshDerived = refreshDerived
          )
        )
      }

    case req @ GET -> Root / "demo" / "guide" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val bootstrapIfMissing = support.queryBooleanParam(req, "bootstrapIfMissing").getOrElse(true)
        val refreshDerived = support.queryBooleanParam(req, "refreshDerived").getOrElse(true)
        support.optionJsonResponse(
          support.app.demoScenarioService.guide(
            variant = variant,
            bootstrapIfMissing = bootstrapIfMissing,
            refreshDerived = refreshDerived
          )
        )
      }

    case req @ GET -> Root / "demo" / "widgets" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val bootstrapIfMissing = support.queryBooleanParam(req, "bootstrapIfMissing").getOrElse(true)
        val refreshDerived = support.queryBooleanParam(req, "refreshDerived").getOrElse(true)
        support.optionJsonResponse(
          support.app.demoScenarioService.widgets(
            variant = variant,
            bootstrapIfMissing = bootstrapIfMissing,
            refreshDerived = refreshDerived
          )
        )
      }

    case req @ GET -> Root / "demo" / "actions" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val bootstrapIfMissing = support.queryBooleanParam(req, "bootstrapIfMissing").getOrElse(true)
        val refreshDerived = support.queryBooleanParam(req, "refreshDerived").getOrElse(true)
        support.optionJsonResponse(
          support.app.demoScenarioService.actionCatalog(
            variant = variant,
            bootstrapIfMissing = bootstrapIfMissing,
            refreshDerived = refreshDerived
          )
        )
      }

    case req @ POST -> Root / "demo" / "actions" / actionCode =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val bootstrapIfMissing = support.queryBooleanParam(req, "bootstrapIfMissing").getOrElse(true)
        support.optionJsonResponse(
          support.app.demoScenarioService.executeAction(
            variant = variant,
            action = support.parseEnum("action", actionCode)(DemoScenarioActionCode.valueOf),
            bootstrapIfMissing = bootstrapIfMissing
          )
        )
      }

    case req @ POST -> Root / "demo" / "bootstrap" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val refreshDerived = support.queryBooleanParam(req, "refreshDerived").getOrElse(true)
        support.jsonResponse(Status.Ok, support.app.demoScenarioService.bootstrapScenario(variant = variant, refreshDerived = refreshDerived))
      }

    case req @ POST -> Root / "demo" / "refresh" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val bootstrapIfMissing = support.queryBooleanParam(req, "bootstrapIfMissing").getOrElse(true)
        support.optionJsonResponse(
          support.app.demoScenarioService.refreshScenario(variant = variant, bootstrapIfMissing = bootstrapIfMissing)
        )
      }

    case req @ POST -> Root / "demo" / "reset" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val refreshDerived = support.queryBooleanParam(req, "refreshDerived").getOrElse(true)
        support.jsonResponse(Status.Ok, support.app.demoScenarioService.bootstrapScenario(variant = variant, refreshDerived = refreshDerived))
      }
  }
