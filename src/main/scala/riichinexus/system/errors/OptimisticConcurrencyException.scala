package riichinexus.system.errors

final case class OptimisticConcurrencyException(
    aggregateType: String,
    aggregateId: String,
    expectedVersion: Int,
    actualVersion: Option[Int]
) extends IllegalStateException(
      s"Optimistic concurrency conflict for $aggregateType:$aggregateId. Expected version $expectedVersion, actual version ${actualVersion.getOrElse(-1)}"
    )
