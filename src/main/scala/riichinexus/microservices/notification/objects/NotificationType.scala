package riichinexus.microservices.notification.objects

/** 通知中心支持投递给玩家的业务通知类型。
  *
  * 类型覆盖俱乐部申请、赛事邀请、阵容、结算、申诉和评分变化，作为前端图标、文案和跳转策略的稳定依据。
  */
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
