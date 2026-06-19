package riichinexus.microservices.tournament.objects.paifu

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** ScoreChange 表示前后端共享的分数变化 数据结构，包含玩家 ID、delta。 */

final case class ScoreChange(
    playerId: PlayerId,
    delta: Int
)
