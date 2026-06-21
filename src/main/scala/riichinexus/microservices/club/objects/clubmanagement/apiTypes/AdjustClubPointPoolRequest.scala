package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 调整俱乐部可分配点数池的管理请求。
  *
  * `delta` 是本次增减量而不是最终余额，`operatorId` 和 `note` 会支撑后续审计与后台解释。
  */
final case class AdjustClubPointPoolRequest(
    operatorId: String,
    delta: Int,
    note: Option[String] = None
)

object AdjustClubPointPoolRequest:
  given ReadWriter[AdjustClubPointPoolRequest] = macroRW
