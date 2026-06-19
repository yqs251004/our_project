package riichinexus.microservices.tournament.objects.finalization.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** FinalizeTournamentSettlementRequest 表示确认赛事结算请求 的前端请求参数。 */

final case class FinalizeTournamentSettlementRequest(
    operatorId: String,
    note: Option[String] = None
)

object FinalizeTournamentSettlementRequest:
  given ReadWriter[FinalizeTournamentSettlementRequest] = macroRW
