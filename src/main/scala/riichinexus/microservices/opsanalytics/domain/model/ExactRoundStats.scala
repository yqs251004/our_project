package riichinexus.microservices.opsanalytics.domain.model

private[opsanalytics] final case class ExactRoundStats(
    strictTileTrackable: Boolean,
    ukeireSamples: Vector[Int],
    postRiichiDiscardCount: Int,
    safePostRiichiDiscardCount: Int,
    foldDiscardCount: Int
)
