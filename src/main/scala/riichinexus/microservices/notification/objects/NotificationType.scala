package riichinexus.microservices.notification.objects

/** NotificationType 枚举系统通知类型的公开取值。 */
enum NotificationType:
  case ClubApplicationSubmitted
  case ClubApplicationApproved
  case ClubApplicationRejected
  case ClubMemberContributionAdjusted
  case ClubTitleAssigned
  case ClubRelationChangeRequested
  case TournamentClubInvited
  case TournamentPlayerInvited
  case TournamentLineupSelected
  case TournamentSettlementFinalized
  case TournamentTableStarted
  case TournamentAppealFiled
  case TournamentAppealAdjudicated
  case PlayerEloChanged

object NotificationType:
  def toString(notificationType: NotificationType): String =
    notificationType.toString

  def fromString(value: String): NotificationType =
    NotificationType.valueOf(value)
