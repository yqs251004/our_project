package riichinexus.microservices.opsanalytics.objects.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.AdvancedStatsBackfillMode
import upickle.default.{ReadWriter, macroRW}

/** 运维或后台页面发起高级统计重算时提交的请求体。
  *
  * 请求指定操作者、补算模式、可选目标归属和批量上限，后端据此生成一批重算任务并记录触发原因。
  */
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
