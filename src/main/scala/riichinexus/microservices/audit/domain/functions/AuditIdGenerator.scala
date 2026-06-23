package riichinexus.microservices.audit.domain.functions

import riichinexus.microservices.audit.domain.model.{AuditEventId, AuditIdPrefix}

import java.util.UUID

/** AuditIdGenerator 负责生成审计标识符生成器 相关的领域标识符。 */

private[audit] object AuditIdGenerator:
  private def nextId(prefix: AuditIdPrefix): String =
    s"${AuditIdPrefix.toString(prefix)}-${UUID.randomUUID().toString.take(8)}"

  def auditEventId(): AuditEventId = AuditEventId(nextId(AuditIdPrefix.AuditEvent))
