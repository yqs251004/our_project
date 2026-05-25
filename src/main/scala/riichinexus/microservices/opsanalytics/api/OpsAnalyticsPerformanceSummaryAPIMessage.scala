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
    for
      operator <- IO(context.support.principal(operatorId))
      _ <- IO(requireOpsAdmin(context, operator))
      resolvedLimit <- IO(resolveLimit)
      snapshot <- IO(context.support.opsAnalyticsModule.performanceDiagnosticsService.snapshot(limit = resolvedLimit))
    yield snapshot

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)

  private def resolveLimit: Int =
    val resolvedLimit = limit.getOrElse(15)
    require(resolvedLimit > 0, "Input field limit must be positive")
    math.min(resolvedLimit, 100)
