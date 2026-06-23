package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model

import riichinexus.microservices.tournament.objects.paifu.ScoreChange

/** 单次和牌在当前规则下产生的总点数与分数变动。 */
private[yakuanalysis] final case class PointResult(
    points: Int,
    scoreChanges: Vector[ScoreChange]
)
