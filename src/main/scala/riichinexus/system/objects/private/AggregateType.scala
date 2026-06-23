package riichinexus.system.objects.`private`

/** 后端审计、实时事件和乐观锁冲突中使用的稳定聚合类型。
  *
  * 该类型用于约束后端内部可记录的业务聚合名称；序列化到 HTTP、实时事件和数据库索引时仍使用 toString 的稳定字符串值。
  */
enum AggregateType:
  case AccountCredential
  case AuthenticatedSession
  case GuestSession
  case Player
  case Club
  case ClubApplication
  case Tournament
  case TournamentSettlement
  case TournamentTable
  case MahjongTable
  case Notification
  case Dashboard
  case AdvancedStatsTask
  case AdvancedStatsBoard
  case Appeal
  case AppealTicket

object AggregateType:
  def toString(aggregateType: AggregateType): String =
    aggregateType match
      case AggregateType.AccountCredential    => "account-credential"
      case AggregateType.AuthenticatedSession => "authenticated-session"
      case AggregateType.GuestSession         => "guest-session"
      case AggregateType.Player               => "player"
      case AggregateType.Club                 => "club"
      case AggregateType.ClubApplication      => "club-application"
      case AggregateType.Tournament           => "tournament"
      case AggregateType.TournamentSettlement => "tournament-settlement"
      case AggregateType.TournamentTable      => "table"
      case AggregateType.MahjongTable         => "mahjongTable"
      case AggregateType.Notification         => "notification"
      case AggregateType.Dashboard            => "dashboard"
      case AggregateType.AdvancedStatsTask    => "advanced-stats-task"
      case AggregateType.AdvancedStatsBoard   => "advanced-stats-board"
      case AggregateType.Appeal               => "appeal"
      case AggregateType.AppealTicket         => "appeal-ticket"

  def fromString(value: String): AggregateType =
    value match
      case "account-credential"    => AggregateType.AccountCredential
      case "authenticated-session" => AggregateType.AuthenticatedSession
      case "guest-session"         => AggregateType.GuestSession
      case "player"                => AggregateType.Player
      case "club"                  => AggregateType.Club
      case "club-application"      => AggregateType.ClubApplication
      case "tournament"            => AggregateType.Tournament
      case "tournament-settlement" => AggregateType.TournamentSettlement
      case "table"                 => AggregateType.TournamentTable
      case "mahjongTable"          => AggregateType.MahjongTable
      case "notification"          => AggregateType.Notification
      case "dashboard"             => AggregateType.Dashboard
      case "advanced-stats-task"   => AggregateType.AdvancedStatsTask
      case "advanced-stats-board"  => AggregateType.AdvancedStatsBoard
      case "appeal"                => AggregateType.Appeal
      case "appeal-ticket"         => AggregateType.AppealTicket
      case other                   => throw IllegalArgumentException(s"Unknown aggregate type: $other")
