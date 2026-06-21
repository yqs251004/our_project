package riichinexus.microservices.tournament.objects.stage.lineup.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 俱乐部为某个赛事阶段提交阵容的请求体。
  *
  * 请求记录俱乐部、提交人、正选/替补席位和备注，后端会校验成员资格与阵容数量后生成提交快照。
  */
final case class SubmitStageLineupRequest(
    clubId: String,
    operatorId: String,
    seats: Vector[StageLineupSeatRequest],
    note: Option[String] = None
)

object SubmitStageLineupRequest:
  given ReadWriter[SubmitStageLineupRequest] = macroRW
