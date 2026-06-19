package riichinexus.microservices.tournament.objects.stage.lineup.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** SubmitStageLineupRequest 表示提交阶段阵容请求 的前端请求参数。 */

final case class SubmitStageLineupRequest(
    clubId: String,
    operatorId: String,
    seats: Vector[StageLineupSeatRequest],
    note: Option[String] = None
)

object SubmitStageLineupRequest:
  given ReadWriter[SubmitStageLineupRequest] = macroRW
