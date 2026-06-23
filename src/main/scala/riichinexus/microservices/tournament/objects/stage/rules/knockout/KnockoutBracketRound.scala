package riichinexus.microservices.tournament.objects.stage.rules.knockout

import upickle.default.{ReadWriter, macroRW}

/** 淘汰赛 bracket 中同一轮次的对局集合。
  *
  * 前端用 `label` 呈现轮次名称，用 `matches` 渲染该轮所有冠军线、季军战或复活线节点。
  */
final case class KnockoutBracketRound(
    roundNumber: Int,
    label: String,
    matches: Vector[KnockoutBracketMatch]
)

object KnockoutBracketRound:
  given ReadWriter[KnockoutBracketRound] = macroRW
