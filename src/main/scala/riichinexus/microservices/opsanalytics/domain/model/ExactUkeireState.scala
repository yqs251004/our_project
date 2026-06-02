package riichinexus.microservices.opsanalytics.domain.model

private[opsanalytics] final case class ExactUkeireState(
    hand: Vector[Int],
    visibleKnown: Vector[Int],
    samples: Vector[Int],
    trackable: Boolean
)
