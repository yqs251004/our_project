package riichinexus.microservices.audit.domain.auditevent

/** 审计事件的稳定标识符。
  *
  * 该值用于在内部日志、私有查询接口和问题排查记录之间定位同一条审计事件。
  */
final case class AuditEventId(value: String)
