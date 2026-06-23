package riichinexus.microservices.tournament.objects.paifu

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifu.{KyokuDescriptor, ScoreChange}
import upickle.default.{ReadWriter, macroRW}

/** 牌谱摘要中某一小局的分数变化。
  *
  * 它只保留局定位和各玩家得失分，方便列表页展示分数走势而无需加载完整时间线。
  */
final case class PaifuRoundScoreChanges(
    descriptor: KyokuDescriptor,
    scoreChanges: Vector[ScoreChange]
)

object PaifuRoundScoreChanges:
  given ReadWriter[PaifuRoundScoreChanges] = macroRW
