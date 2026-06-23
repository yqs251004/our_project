package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model

import riichinexus.microservices.tournament.objects.paifu.Yaku

/** 已完成番符与点数计算的役种候选。 */
private[yakuanalysis] final case class ScoredYakuCandidate(
    yaku: Vector[Yaku],
    han: Int,
    fu: Int,
    pointResult: PointResult
)
