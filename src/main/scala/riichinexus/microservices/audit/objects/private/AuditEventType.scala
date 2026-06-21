package riichinexus.microservices.audit.objects.`private`

/** 审计日志支持记录的业务事件类型。
  *
  * 枚举项覆盖账号、俱乐部、赛事、申诉和结算等会改变业务状态的动作，作为审计表里的稳定协议值使用。
  */
enum AuditEventType:
  case GuestSessionCreated
  case GuestSessionRevoked
  case GuestSessionUpgraded
  case ClubMemberContributionAdjusted
  case ClubPointPoolAdjusted
  case ClubTreasuryAdjusted
  case ClubTitleAssigned
  case ClubTitleCleared
  case ClubHonorAwarded
  case ClubHonorRevoked
  case ClubApplicationSubmitted
  case ClubApplicationApproved
  case ClubApplicationRejected
  case ClubApplicationWithdrawn
  case ClubRankTreeUpdated
  case ClubRecruitmentPolicyUpdated
  case ClubRelationUpdated
  case PlayerBanned
  case ClubDissolved
  case SuperAdminGranted
  case TournamentAdminAssigned
  case TournamentAdminRevoked
  case TournamentSettlementRecorded
  case TournamentSettlementFinalized
  case AppealTicketFiled
  case AppealTicketAdjudicated
  case AppealTicketReopened
  case AppealTicketWorkflowUpdated

object AuditEventType:
  def toString(eventType: AuditEventType): String =
    eventType.toString

  def fromString(value: String): AuditEventType =
    AuditEventType.valueOf(value)
