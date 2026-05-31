package riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes

import riichinexus.microservices.tournament.objects.settlementmanagement.TournamentSettlementStatus

import riichinexus.domain.model.{PlayerId, TournamentStageId}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TournamentSettlementQuery(
    stageId: Option[TournamentStageId] = None,
    status: Option[TournamentSettlementStatus] = None,
    championId: Option[PlayerId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
