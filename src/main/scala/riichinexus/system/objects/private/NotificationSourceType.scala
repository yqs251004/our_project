package riichinexus.system.objects.`private`

/** 后端内部创建通知时声明来源业务类型的稳定类型。
  *
  * 这个类型约束各微服务提交通知请求时可使用的 sourceType；落库、实时事件和 HTTP 序列化仍使用 toString 给出的稳定字符串。
  */
enum NotificationSourceType:
  case ClubContribution
  case ClubTitle
  case ClubApplication
  case ClubRelationRequest
  case PlayerRating
  case TournamentSettlement
  case TournamentLineup
  case TournamentTable
  case TournamentClubInvitation
  case TournamentPlayerInvitation
  case Appeal

object NotificationSourceType:
  def toString(sourceType: NotificationSourceType): String =
    sourceType match
      case NotificationSourceType.ClubContribution           => "club-contribution"
      case NotificationSourceType.ClubTitle                  => "club-title"
      case NotificationSourceType.ClubApplication            => "club-application"
      case NotificationSourceType.ClubRelationRequest        => "club-relation-request"
      case NotificationSourceType.PlayerRating               => "player-rating"
      case NotificationSourceType.TournamentSettlement       => "tournament-settlement"
      case NotificationSourceType.TournamentLineup           => "tournament-lineup"
      case NotificationSourceType.TournamentTable            => "tournament-table"
      case NotificationSourceType.TournamentClubInvitation   => "tournament-club-invitation"
      case NotificationSourceType.TournamentPlayerInvitation => "tournament-player-invitation"
      case NotificationSourceType.Appeal                     => "appeal"

  def fromString(value: String): NotificationSourceType =
    value match
      case "club-contribution"             => NotificationSourceType.ClubContribution
      case "club-title"                    => NotificationSourceType.ClubTitle
      case "club-application"              => NotificationSourceType.ClubApplication
      case "club-relation-request"         => NotificationSourceType.ClubRelationRequest
      case "player-rating"                 => NotificationSourceType.PlayerRating
      case "tournament-settlement"         => NotificationSourceType.TournamentSettlement
      case "tournament-lineup"             => NotificationSourceType.TournamentLineup
      case "tournament-table"              => NotificationSourceType.TournamentTable
      case "tournament-club-invitation"    => NotificationSourceType.TournamentClubInvitation
      case "tournament-player-invitation"  => NotificationSourceType.TournamentPlayerInvitation
      case "appeal"                        => NotificationSourceType.Appeal
      case other                           => throw IllegalArgumentException(s"Unknown notification source type: $other")
