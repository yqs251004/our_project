package riichinexus.microservices.opsanalytics.domain.model

final case class PlayerRoundStats(
    shantenPath: Vector[Int],
    won: Boolean,
    dealtIn: Boolean,
    resultDelta: Int,
    riichiDeclared: Boolean,
    callCount: Int,
    pressureResponseCount: Int,
    postRiichiDealIn: Boolean,
    foldLikeResponse: Boolean,
    shantenImprovement: Double,
    exactUkeireSamples: Vector[Int],
    exactDefenseSampleCount: Int,
    exactSafeDefenseCount: Int,
    exactFoldCount: Int,
    strictTileTrackable: Boolean
) derives CanEqual
