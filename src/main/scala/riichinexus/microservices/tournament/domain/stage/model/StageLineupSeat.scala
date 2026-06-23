package riichinexus.microservices.tournament.domain.stage.model


import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.SeatWind

import riichinexus.system.json.JsonCodecs.given

/** 俱乐部在阶段阵容中提交的一名选手席位。
  *
  * `preferredWind` 表示期望座位风，`reserve` 标记该选手是否作为替补进入调度池。
  */
final case class StageLineupSeat(
    playerId: PlayerId,
    preferredWind: Option[SeatWind] = None,
    reserve: Boolean = false
)
