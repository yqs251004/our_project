package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.system.json.JsonCodecs.given

/** 一次比赛结算后某位玩家的 Elo 变化。
  *
  * 它只记录玩家和增减量，方便评分计算结果被玩家服务、通知服务和审计流程分别消费。
  */
final case class RatingChange(
    playerId: PlayerId,
    delta: Int
)
