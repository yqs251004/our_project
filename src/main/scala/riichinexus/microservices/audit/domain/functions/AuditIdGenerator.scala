package riichinexus.microservices.audit.domain.functions

import riichinexus.microservices.audit.domain.auditevent.AuditEventId

import java.util.UUID

object AuditIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def auditEventId(): AuditEventId = AuditEventId(nextId("audit"))
