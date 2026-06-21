package riichinexus.microservices.tournament.objects.paifu

/** 可回放、可统计的一整场牌谱归档。
  *
  * 它包含牌谱身份、归属元数据、所有小局和最终名次，是牌谱详情页、战绩归档和高级统计的原始数据来源。
  */
final case class Paifu(
    id: PaifuId,
    metadata: PaifuMetadata,
    rounds: Vector[PaifuRound],
    finalStandings: Vector[FinalStanding]
)
