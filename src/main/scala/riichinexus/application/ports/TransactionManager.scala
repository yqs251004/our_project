package riichinexus.application.ports

final case class OptimisticConcurrencyException(
    aggregateType: String,
    aggregateId: String,
    expectedVersion: Int,
    actualVersion: Option[Int]
) extends IllegalStateException(
      s"Optimistic concurrency conflict for $aggregateType:$aggregateId. Expected version $expectedVersion, actual version ${actualVersion.getOrElse(-1)}"
    )

trait TransactionManager:
  def inTransaction[A](operation: => A): A

object NoOpTransactionManager extends TransactionManager:
  override def inTransaction[A](operation: => A): A =
    operation
