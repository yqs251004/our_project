package riichinexus.microservices.tournament.mahjongcore.domain.yakuanalysis.model

import riichinexus.microservices.tournament.mahjongcore.domain.handanalysis.model.MahjongHandDecomposition
import riichinexus.microservices.tournament.objects.paifu.Yaku

/** 一种普通手拆解路径下识别出的役种候选结果。 */
private[mahjongcore] final case class MahjongYakuCandidate(
    yaku: Vector[Yaku],
    decomposition: Option[MahjongHandDecomposition]
)
