package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TournamentSettlementAdjustmentView(
    playerId: String,
    label: String,
    amount: Long,
    note: Option[String]
) derives CanEqual

object TournamentSettlementAdjustmentView:
  def fromDomain(adjustment: TournamentSettlementAdjustment): TournamentSettlementAdjustmentView =
    TournamentSettlementAdjustmentView(adjustment.playerId.value, adjustment.label, adjustment.amount, adjustment.note)

  given ReadWriter[TournamentSettlementAdjustmentView] = macroRW

