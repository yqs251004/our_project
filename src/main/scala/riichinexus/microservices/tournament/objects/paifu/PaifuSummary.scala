package riichinexus.microservices.tournament.objects.paifu

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifu.FinalStanding
import upickle.default.{ReadWriter, macroRW}

/** 牌谱列表页和战绩页使用的轻量摘要。
  *
  * 摘要包含归属信息、局数、玩家、最终排名和每局分数变化，足够列表预览但不包含完整动作时间线。
  */
final case class PaifuSummary(
    paifuId: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    recordedAt: String,
    source: String,
    matchRecordId: Option[String],
    totalHands: Int,
    playerIds: Vector[String],
    finalStandings: Vector[FinalStanding],
    roundScoreChanges: Vector[PaifuRoundScoreChanges]
)

object PaifuSummary:
  given ReadWriter[PaifuSummary] = macroRW
