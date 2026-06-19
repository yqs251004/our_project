package riichinexus.microservices.tournament.objects.`private`

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** StageLineupSeatPrivateView 表示后端内部使用的阶段阵容座位后端内部视图 read model，包含玩家 ID、reserve。 */

final case class StageLineupSeatPrivateView(
    playerId: PlayerId,
    reserve: Boolean
)
