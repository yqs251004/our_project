package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.microservices.opsanalytics.domain.functions.DashboardFunctions
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端重算流程重置指定主体的仪表盘读模型。 */
final case class ResetDashboardPrivateAPIMessage(
    owner: DashboardOwner,
    at: Instant
) extends APIMessage[Dashboard]:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    for
      existingVersion <- loadExistingVersion(context, owner)
      dashboard <- saveResetDashboard(context, owner, at, existingVersion)
    yield dashboard

  private def loadExistingVersion(context: ApiPlanContext, owner: DashboardOwner): IO[Int] =
    IO.blocking(DashboardTable.findByOwner(context.connection, owner).map(_.version).getOrElse(0))

  private def saveResetDashboard(
      context: ApiPlanContext,
      owner: DashboardOwner,
      at: Instant,
      existingVersion: Int
  ): IO[Dashboard] =
    IO.blocking {
      DashboardTable.save(
        context.connection,
        DashboardFunctions.empty(owner, at).copy(version = existingVersion)
      )
    }
