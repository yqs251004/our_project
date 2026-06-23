package riichinexus.microservices.auth.domain.session.model

/** 认证微服务内部生成领域 ID 时使用的稳定前缀。
  *
  * 该类型只约束后端 ID 生成器可选的前缀，不属于公开 API，也不需要前端镜像。
  */
private[auth] enum AuthIdPrefix:
  case GuestSession

object AuthIdPrefix:
  def toString(prefix: AuthIdPrefix): String =
    prefix match
      case AuthIdPrefix.GuestSession => "guest"
