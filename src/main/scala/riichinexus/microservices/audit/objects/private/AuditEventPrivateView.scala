package riichinexus.microservices.audit.objects.`private`

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.objects.`private`.AggregateType

/** 审计服务对内部调用方返回的完整事件视图。
  *
  * 该视图保留聚合定位、操作者、发生时间和明细字段，供管理后台或其他微服务追溯一次业务动作；
  * 它不是公开页面的展示 DTO，因此可以包含内部聚合名称与记录注记。
  */
final case class AuditEventPrivateView(
    id: String,
    aggregateType: AggregateType,
    aggregateId: String,
    eventType: AuditEventType,
    occurredAt: Instant,
    actorId: Option[PlayerId] = None,
    details: Map[String, String] = Map.empty,
    note: Option[String] = None
)
