package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import riichinexus.microservices.tournament.domain.model.RoundSettlement

final case class TournamentPaifuRoundSettlementView(
    riichiSticksDelta: Int,
    honbaPayment: Int,
    notes: Vector[String]
) derives CanEqual

object TournamentPaifuRoundSettlementView:
  given ReadWriter[TournamentPaifuRoundSettlementView] = macroRW

  def fromDomain(settlement: RoundSettlement): TournamentPaifuRoundSettlementView =
    TournamentPaifuRoundSettlementView(
      riichiSticksDelta = settlement.riichiSticksDelta,
      honbaPayment = settlement.honbaPayment,
      notes = settlement.notes
    )
