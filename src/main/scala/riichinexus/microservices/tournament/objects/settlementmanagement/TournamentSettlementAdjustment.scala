package riichinexus.microservices.tournament.objects.settlementmanagement

import riichinexus.domain.model.PlayerId

final case class TournamentSettlementAdjustment(
    playerId: PlayerId,
    label: String,
    amount: Long,
    note: Option[String] = None
) derives CanEqual
