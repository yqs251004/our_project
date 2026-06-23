package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongLegalAction

import riichinexus.system.json.JsonCodecs.given

/** 待响应窗口中某位玩家提交的鸣牌、荣和或跳过响应。
  *
  * 状态机会收集所有相关响应后按优先级决定是否进入副露、和牌或继续摸切流程。
  */
final case class MahjongCallResponse(
    playerId: PlayerId,
    action: MahjongLegalAction
)
