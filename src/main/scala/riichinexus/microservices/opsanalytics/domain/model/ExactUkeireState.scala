package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.system.json.JsonCodecs.given
/** ExactUkeireState 表示后端领域中的ExactUkeire状态 状态，包含hand、visibleKnown、samples、trackable。 */
final case class ExactUkeireState(
    hand: Vector[Int],
    visibleKnown: Vector[Int],
    samples: Vector[Int],
    trackable: Boolean
)