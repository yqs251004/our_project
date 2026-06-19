package riichinexus.microservices.tournament.objects.paifumanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifumanagement.FinalStanding
import upickle.default.ReadWriter

/** PaifuSummary 表示前后端共享的牌谱摘要 数据结构，包含paifuId、牌桌 ID、赛事 ID、阶段 ID、recordedAt、source等。 */

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
) derives ReadWriter
