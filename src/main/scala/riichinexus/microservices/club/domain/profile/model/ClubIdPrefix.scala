package riichinexus.microservices.club.domain.profile.model

/** 俱乐部微服务内部生成领域 ID 时使用的稳定前缀。
  *
  * 该类型只约束后端 ID 生成器可选的前缀，不属于公开 API，也不需要前端镜像。
  */
private[club] enum ClubIdPrefix:
  case Club
  case MembershipApplication

object ClubIdPrefix:
  def toString(prefix: ClubIdPrefix): String =
    prefix match
      case ClubIdPrefix.Club                  => "club"
      case ClubIdPrefix.MembershipApplication => "membership"
