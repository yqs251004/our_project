package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.domain.model.PlayerId

private[opsanalytics] final case class ExactDefenseState(
    riichiDiscards: Map[PlayerId, Set[Int]],
    playerDeclaredRiichi: Boolean,
    postRiichiDiscardCount: Int,
    safePostRiichiDiscardCount: Int,
    foldDiscardCount: Int,
    publicVisible: Vector[Int]
) derives CanEqual
