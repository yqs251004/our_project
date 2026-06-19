package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.system.json.JsonCodecs.given
/** ExactRoundStats 表示后端领域中的Exact小局统计 状态，包含strictTileTrackable、ukeireSamples、postRiichiDiscardCount、safePostRiichiDiscardCount、foldDiscardCount。 */
final case class ExactRoundStats(
    strictTileTrackable: Boolean,
    ukeireSamples: Vector[Int],
    postRiichiDiscardCount: Int,
    safePostRiichiDiscardCount: Int,
    foldDiscardCount: Int
)