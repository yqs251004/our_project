package riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** StageLineupSeatRequest 表示阶段阵容座位请求 的前端请求参数。 */

final case class StageLineupSeatRequest(
    playerId: String,
    preferredWind: Option[String] = None,
    reserve: Boolean = false
)

object StageLineupSeatRequest:
  given ReadWriter[StageLineupSeatRequest] = macroRW
