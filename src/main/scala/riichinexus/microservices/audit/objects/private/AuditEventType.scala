package riichinexus.microservices.audit.objects.`private`

/** AuditEventType 枚举审计事件类型的私有审计协议取值。 */
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
