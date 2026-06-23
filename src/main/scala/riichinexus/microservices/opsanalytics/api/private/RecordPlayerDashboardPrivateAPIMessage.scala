package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.DashboardFunctions
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import riichinexus.microservices.tournament.api.matchrecord.`private`.LoadPlayerMatchRecordsForOpsAnalyticsPrivateAPIMessage
import riichinexus.microservices.tournament.api.paifu.`private`.LoadPlayerPaifusForOpsAnalyticsPrivateAPIMessage
/** 供后端服务计算并记录玩家仪表盘读模型。 */
final case class RecordPlayerDashboardPrivateAPIMessage(
    playerId: PlayerId,
    at: Instant
) extends APIMessage[Dashboard]:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    for
      records <- LoadPlayerMatchRecordsForOpsAnalyticsPrivateAPIMessage(playerId).plan(context)
      paifus <- LoadPlayerPaifusForOpsAnalyticsPrivateAPIMessage(playerId).plan(context)
      existingVersion <- IO.blocking {
        DashboardTable.findByOwner(context.connection, DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
      }
      saved <- IO.blocking {
        DashboardTable.save(
          context.connection,
          DashboardFunctions.buildPlayerDashboard(playerId, records, paifus.flatMap(_.rounds), at, existingVersion)
        )
      }
    yield saved
