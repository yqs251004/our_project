package riichinexus.microservices.opsanalytics.objects.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.AdvancedStatsBackfillMode
import upickle.default.{ReadWriter, macroRW}

/** AdvancedStatsRecomputeRequest 表示高级统计重算请求 的前端请求参数。 */

final case class AdvancedStatsRecomputeRequest(
    operatorId: PlayerId,
    mode: AdvancedStatsBackfillMode = AdvancedStatsBackfillMode.Full,
    ownerType: Option[String] = None,
    ownerId: Option[String] = None,
    reason: Option[String] = None,
    limit: Int = 500
)

object AdvancedStatsRecomputeRequest:
  given ReadWriter[AdvancedStatsRecomputeRequest] = macroRW
