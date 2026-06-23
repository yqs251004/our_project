package riichinexus.microservices.tournament.appeal.domain.model

/** 申诉子域内部生成领域 ID 时使用的稳定前缀。
  *
  * 该类型只约束后端 ID 生成器可选的前缀，不属于公开 API，也不需要前端镜像。
  */
private[appeal] enum AppealIdPrefix:
  case AppealTicket

object AppealIdPrefix:
  def toString(prefix: AppealIdPrefix): String =
    prefix match
      case AppealIdPrefix.AppealTicket => "appeal"
