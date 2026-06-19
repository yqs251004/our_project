package riichinexus.microservices.audit.domain.functions

import riichinexus.microservices.audit.domain.auditevent.AuditEventId

import java.util.UUID

/** AuditIdGenerator 负责生成审计标识符生成器 相关的领域标识符。 */

private[audit] object AuditIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def auditEventId(): AuditEventId = AuditEventId(nextId("audit"))
