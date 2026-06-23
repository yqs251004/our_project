package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsBoardFunctions
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.microservices.tournament.api.matchrecord.`private`.LoadPlayerMatchRecordsForOpsAnalyticsPrivateAPIMessage
import riichinexus.microservices.tournament.api.paifu.`private`.LoadPlayerPaifusForOpsAnalyticsPrivateAPIMessage
/** 供后端服务计算并记录玩家高级统计读模型。 */
final case class RecordPlayerAdvancedStatsBoardPrivateAPIMessage(
    playerId: PlayerId,
    at: Instant
) extends APIMessage[AdvancedStatsBoard]:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    for
      records <- LoadPlayerMatchRecordsForOpsAnalyticsPrivateAPIMessage(playerId).plan(context)
      paifus <- LoadPlayerPaifusForOpsAnalyticsPrivateAPIMessage(playerId).plan(context)
      existingVersion <- IO.blocking {
        AdvancedStatsBoardTable.findByOwner(context.connection, DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
      }
      saved <- IO.blocking {
        AdvancedStatsBoardTable.save(
          context.connection,
          AdvancedStatsBoardFunctions.buildPlayerBoard(playerId, records, paifus, at, existingVersion)
        )
      }
    yield saved
