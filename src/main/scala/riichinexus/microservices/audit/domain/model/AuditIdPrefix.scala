package riichinexus.microservices.audit.domain.model

/** 审计微服务内部生成领域 ID 时使用的稳定前缀。
  *
  * 该类型只约束后端 ID 生成器可选的前缀，不属于公开 API，也不需要前端镜像。
  */
private[audit] enum AuditIdPrefix:
  case AuditEvent

object AuditIdPrefix:
  def toString(prefix: AuditIdPrefix): String =
    prefix match
      case AuditIdPrefix.AuditEvent => "audit"
