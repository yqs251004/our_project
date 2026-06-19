package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsBoardFunctions
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端重算流程重置指定主体的高级统计读模型。 */
final case class ResetAdvancedStatsBoardPrivateAPIMessage(
    owner: DashboardOwner,
    at: Instant
) extends APIMessage[AdvancedStatsBoard]:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    for
      existingVersion <- loadExistingVersion(context, owner)
      board <- saveResetBoard(context, owner, at, existingVersion)
    yield board

  private def loadExistingVersion(context: ApiPlanContext, owner: DashboardOwner): IO[Int] =
    IO.blocking(AdvancedStatsBoardTable.findByOwner(context.connection, owner).map(_.version).getOrElse(0))

  private def saveResetBoard(
      context: ApiPlanContext,
      owner: DashboardOwner,
      at: Instant,
      existingVersion: Int
  ): IO[AdvancedStatsBoard] =
    IO.blocking {
      AdvancedStatsBoardTable.save(
        context.connection,
        AdvancedStatsBoardFunctions.empty(owner, at).copy(version = existingVersion)
      )
    }
