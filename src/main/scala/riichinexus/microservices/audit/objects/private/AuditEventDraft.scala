package riichinexus.microservices.audit.objects.`private`

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.objects.`private`.AggregateType

/** 私有审计 API 接收的事件草稿。
  *
  * 调用方在业务操作完成后用它记录被影响的聚合、事件类型、操作者和补充明细；
  * 持久化时再由审计服务分配事件 ID，因此这里刻意不携带数据库主键。
  */
final case class AuditEventDraft(
    aggregateType: AggregateType,
    aggregateId: String,
    eventType: AuditEventType,
    occurredAt: Instant,
    actorId: Option[PlayerId] = None,
    details: Map[String, String] = Map.empty,
    note: Option[String] = None
)
