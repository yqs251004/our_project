package riichinexus.application.ports

trait TransactionManager:
  def inTransaction[A](operation: => A): A

object NoOpTransactionManager extends TransactionManager:
  override def inTransaction[A](operation: => A): A =
    operation
