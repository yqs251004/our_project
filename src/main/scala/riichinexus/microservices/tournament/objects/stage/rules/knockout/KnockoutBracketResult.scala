package riichinexus.microservices.tournament.objects.stage.rules.knockout

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 淘汰赛单场对局中某位玩家的晋级结果。
  *
  * 它把牌桌成绩压缩成名次、最终点数和是否晋级，供 bracket 更新下一轮席位。
  */
final case class KnockoutBracketResult(
    playerId: PlayerId,
    placement: Int,
    finalPoints: Int,
    advanced: Boolean
) derives ReadWriter
