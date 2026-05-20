package riichinexus.microservices.opsanalytics.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import upickle.default.*

final case class OpsAnalyticsPerformanceSummaryAPIMessage(
    operatorId: PlayerId,
    limit: Option[Int] = None
) extends APIMessage[PerformanceDiagnosticsSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PerformanceDiagnosticsSnapshot] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
      val resolvedLimit = limit.getOrElse(15)
      require(resolvedLimit > 0, "Input field limit must be positive")
      context.support.opsAnalyticsModule.performanceDiagnosticsService.snapshot(limit = math.min(resolvedLimit, 100))
    }
