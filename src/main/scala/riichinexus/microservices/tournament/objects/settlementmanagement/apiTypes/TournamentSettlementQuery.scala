package riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes

import riichinexus.microservices.tournament.objects.settlementmanagement.TournamentSettlementStatus

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStageId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** TournamentSettlementQuery 表示赛事结算查询 的列表或详情查询条件，包含阶段 ID、状态、championId、数量限制、分页偏移。 */

final case class TournamentSettlementQuery(
    stageId: Option[TournamentStageId] = None,
    status: Option[TournamentSettlementStatus] = None,
    championId: Option[PlayerId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
