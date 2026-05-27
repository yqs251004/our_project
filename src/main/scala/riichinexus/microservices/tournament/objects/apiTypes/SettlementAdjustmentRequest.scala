package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class SettlementAdjustmentRequest(
    playerId: String,
    label: String,
    amount: Long,
    note: Option[String] = None
):
  def adjustment: TournamentSettlementAdjustment =
    TournamentSettlementAdjustment(
      playerId = PlayerId(playerId),
      label = label,
      amount = amount,
      note = note
    )

object SettlementAdjustmentRequest:
  given ReadWriter[SettlementAdjustmentRequest] = macroRW

