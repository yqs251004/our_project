package riichinexus.microservices.opsanalytics.router

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.microservices.opsanalytics.api.DemoScenarioService
import riichinexus.microservices.opsanalytics.api.responses.*
import riichinexus.microservices.opsanalytics.api.responses.DemoScenarioResponses.given
import riichinexus.api.http.RouteSupport

object DemoScenarioRouter:
  private final case class Dependencies(service: DemoScenarioService)

  private def dependencies(support: RouteSupport): Dependencies =
    Dependencies(service = support.opsAnalyticsModule.demoScenarioService)

  def routes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ GET -> Root / "demo" / "summary" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val bootstrapIfMissing = support.queryBooleanParam(req, "bootstrapIfMissing").getOrElse(false)
        val refreshDerived = support.queryBooleanParam(req, "refreshDerived").getOrElse(true)
        val summary = deps.service.currentScenario(variant = variant, refreshDerived = refreshDerived)
          .orElse {
            if bootstrapIfMissing then Some(deps.service.bootstrapScenario(variant = variant, refreshDerived = refreshDerived))
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
          deps.service.currentReadiness(
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
          deps.service.guide(
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
          deps.service.widgets(
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
          deps.service.actionCatalog(
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
          deps.service.executeAction(
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
        support.jsonResponse(Status.Ok, deps.service.bootstrapScenario(variant = variant, refreshDerived = refreshDerived))
      }

    case req @ POST -> Root / "demo" / "refresh" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val bootstrapIfMissing = support.queryBooleanParam(req, "bootstrapIfMissing").getOrElse(true)
        support.optionJsonResponse(
          deps.service.refreshScenario(variant = variant, bootstrapIfMissing = bootstrapIfMissing)
        )
      }

    case req @ POST -> Root / "demo" / "reset" =>
      support.handled {
        val variant = support.queryDemoScenarioVariant(req)
        val refreshDerived = support.queryBooleanParam(req, "refreshDerived").getOrElse(true)
        support.jsonResponse(Status.Ok, deps.service.bootstrapScenario(variant = variant, refreshDerived = refreshDerived))
      }
  }

