package riichinexus.microservices.tournament.objects.paifumanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifumanagement.{KyokuDescriptor, ScoreChange}
import upickle.default.ReadWriter

/** PaifuRoundScoreChanges 表示前后端共享的牌谱小局分数Changes 数据结构，包含descriptor、scoreChanges。 */

final case class PaifuRoundScoreChanges(
    descriptor: KyokuDescriptor,
    scoreChanges: Vector[ScoreChange]
) derives ReadWriter
