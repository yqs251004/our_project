package riichinexus.microservices.player.domain.model

/** 玩家微服务内部生成领域 ID 时使用的稳定前缀。
  *
  * 该类型只约束后端 ID 生成器可选的前缀，不属于公开 API，也不需要前端镜像。
  */
private[player] enum PlayerIdPrefix:
  case Player

object PlayerIdPrefix:
  def toString(prefix: PlayerIdPrefix): String =
    prefix match
      case PlayerIdPrefix.Player => "player"
