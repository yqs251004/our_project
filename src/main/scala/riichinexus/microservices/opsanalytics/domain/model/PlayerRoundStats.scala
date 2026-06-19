package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.system.json.JsonCodecs.given
/** PlayerRoundStats 表示后端领域中的玩家小局统计 状态，包含shantenPath、won、dealtIn、resultDelta、riichiDeclared、callCount等。 */
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
)