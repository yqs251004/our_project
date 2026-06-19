package riichinexus.microservices.tournament.objects.settlementmanagement

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** TournamentSettlementAdjustment 表示前后端共享的赛事结算调整 数据结构，包含玩家 ID、label、amount、note。 */

final case class TournamentSettlementAdjustment(
    playerId: PlayerId,
    label: String,
    amount: Long,
    note: Option[String] = None
)
