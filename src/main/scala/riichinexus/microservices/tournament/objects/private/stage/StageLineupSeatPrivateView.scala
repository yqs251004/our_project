package riichinexus.microservices.tournament.objects.`private`.stage

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** 阶段阵容提交中单个玩家席位的内部表示。
  *
  * `reserve` 区分正选和替补，使排桌与阵容审核逻辑可以在不读取公开 DTO 的情况下解析可用名单。
  */
final case class StageLineupSeatPrivateView(
    playerId: PlayerId,
    reserve: Boolean
)
