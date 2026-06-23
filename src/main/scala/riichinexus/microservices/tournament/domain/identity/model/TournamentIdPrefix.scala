package riichinexus.microservices.tournament.domain.identity.model

/** 赛事微服务内部生成领域 ID 时使用的稳定前缀。
  *
  * 该类型只约束后端 ID 生成器可选的前缀，不属于公开 API，也不需要前端镜像。
  */
private[tournament] enum TournamentIdPrefix:
  case Tournament
  case Stage
  case Table
  case Paifu
  case MatchRecord
  case LineupSubmission
  case SettlementSnapshot

object TournamentIdPrefix:
  def toString(prefix: TournamentIdPrefix): String =
    prefix match
      case TournamentIdPrefix.Tournament         => "tournament"
      case TournamentIdPrefix.Stage              => "stage"
      case TournamentIdPrefix.Table              => "table"
      case TournamentIdPrefix.Paifu              => "paifu"
      case TournamentIdPrefix.MatchRecord        => "record"
      case TournamentIdPrefix.LineupSubmission   => "lineup"
      case TournamentIdPrefix.SettlementSnapshot => "settlement"
