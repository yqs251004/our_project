package riichinexus.microservices.tournament.objects.paifumanagement


/** Paifu 表示前后端共享的牌谱 数据结构，包含 ID、metadata、rounds、finalStandings。 */


final case class Paifu(
    id: PaifuId,
    metadata: PaifuMetadata,
    rounds: Vector[PaifuRound],
    finalStandings: Vector[FinalStanding]
)
