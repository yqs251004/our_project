package riichinexus.microservices.tournament.objects.paifu

import riichinexus.microservices.player.objects.PlayerId

/** 一小局结算后某位玩家的点数变化。
  *
  * `delta` 使用正负值表达得失分，所有玩家的变化合在一起用于结果面板和对局记录归档。
  */
final case class ScoreChange(
    playerId: PlayerId,
    delta: Int
)
