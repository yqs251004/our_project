package riichinexus.microservices.tournament.objects.finalization.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 将某个结算草稿确认为最终结算时提交的请求体。
  *
  * `operatorId` 记录确认人，`note` 可说明最终确认的背景；确认后结算会进入不可随意修改的 finalized 状态。
  */
final case class FinalizeTournamentSettlementRequest(
    operatorId: String,
    note: Option[String] = None
)

object FinalizeTournamentSettlementRequest:
  given ReadWriter[FinalizeTournamentSettlementRequest] = macroRW
