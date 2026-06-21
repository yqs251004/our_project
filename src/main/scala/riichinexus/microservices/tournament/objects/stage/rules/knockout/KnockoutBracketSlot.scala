package riichinexus.microservices.tournament.objects.stage.rules.knockout

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 淘汰赛对阵中一个参赛席位的来源。
  *
  * 席位可能直接绑定种子玩家，也可能是轮空，或来自上一场比赛的指定名次，前端据此渲染尚未解锁的 bracket。
  */
final case class KnockoutBracketSlot(
    seed: Int,
    playerId: Option[PlayerId],
    bye: Boolean = false,
    sourceMatchId: Option[String] = None,
    sourcePlacement: Option[Int] = None
) derives ReadWriter
