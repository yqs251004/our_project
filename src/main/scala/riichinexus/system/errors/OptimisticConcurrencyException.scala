package riichinexus.system.errors

/** 聚合版本不匹配时抛出的乐观并发异常。
  *
  * 异常记录聚合类型、聚合 ID、调用方预期版本和实际版本，便于 API 返回冲突错误并帮助排查并发写入。
  */
final case class OptimisticConcurrencyException(
    aggregateType: String,
    aggregateId: String,
    expectedVersion: Int,
    actualVersion: Option[Int]
) extends IllegalStateException(
      s"Optimistic concurrency conflict for $aggregateType:$aggregateId. Expected version $expectedVersion, actual version ${actualVersion.getOrElse(-1)}"
    )
