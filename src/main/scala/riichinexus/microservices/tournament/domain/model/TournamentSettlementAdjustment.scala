package riichinexus.microservices.tournament.domain.model

import riichinexus.domain.model.PlayerId

final case class TournamentSettlementAdjustment(
    playerId: PlayerId,
    label: String,
    amount: Long,
    note: Option[String] = None
) derives CanEqual:
  require(label.trim.nonEmpty, "Tournament settlement adjustment label cannot be empty")
