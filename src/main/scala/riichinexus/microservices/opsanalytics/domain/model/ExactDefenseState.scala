package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.system.json.JsonCodecs.given
/** ExactDefenseState 表示后端领域中的ExactDefense状态 状态，包含riichiDiscards、playerDeclaredRiichi、postRiichiDiscardCount、safePostRiichiDiscardCount、foldDiscardCount、publicVisible。 */
final case class ExactDefenseState(
    riichiDiscards: Map[PlayerId, Set[Int]],
    playerDeclaredRiichi: Boolean,
    postRiichiDiscardCount: Int,
    safePostRiichiDiscardCount: Int,
    foldDiscardCount: Int,
    publicVisible: Vector[Int]
)