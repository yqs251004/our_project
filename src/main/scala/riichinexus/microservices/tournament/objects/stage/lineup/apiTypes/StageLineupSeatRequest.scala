package riichinexus.microservices.tournament.objects.stage.lineup.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 阶段阵容提交中单个玩家席位的请求项。
  *
  * `preferredWind` 可表达期望座位风，`reserve` 区分替补与正选，最终是否采用仍由阶段排桌逻辑决定。
  */
final case class StageLineupSeatRequest(
    playerId: String,
    preferredWind: Option[String] = None,
    reserve: Boolean = false
)

object StageLineupSeatRequest:
  given ReadWriter[StageLineupSeatRequest] = macroRW
