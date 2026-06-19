package riichinexus.microservices.tournament.domain.stage.model


import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

import riichinexus.system.json.JsonCodecs.given
/** StageLineupSeat 表示后端领域中的阶段阵容座位状态或规则，包含玩家 ID、preferredWind、reserve。 */
final case class StageLineupSeat(
    playerId: PlayerId,
    preferredWind: Option[SeatWind] = None,
    reserve: Boolean = false
)