package riichinexus.system.realtime.objects

/** 实时事件 `sourceEventType` 的细分来源类型。
  *
  * `RealtimeEventType` 表示客户端刷新哪类数据；本枚举表示触发该实时事件的具体业务来源。
  * 来源可能来自审计事件、通知类型或麻将公开动作，因此在实时协议层统一收敛成一个枚举。
  */
enum RealtimeSourceEventType:
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
  case ClubRelationChangeRequested
  case PlayerBanned
  case PlayerEloChanged
  case ClubDissolved
  case SuperAdminGranted
  case TournamentAdminAssigned
  case TournamentAdminRevoked
  case TournamentLineupSubmitted
  case TournamentSettlementRecorded
  case TournamentSettlementFinalized
  case TournamentClubInvited
  case TournamentPlayerInvited
  case TournamentLineupSelected
  case TournamentTableStarted
  case MahjongTableStarted
  case MahjongTableReset
  case MahjongTableRoundAdvanced
  case MahjongTableArchived
  case TournamentAppealFiled
  case TournamentAppealAdjudicated
  case AppealTicketFiled
  case AppealTicketAdjudicated
  case AppealTicketReopened
  case AppealTicketWorkflowUpdated
  case Draw
  case Discard
  case Chi
  case Pon
  case Kan
  case Riichi
  case DoraReveal
  case Win
  case DrawGame
  case AddedKan
  case ClosedKan
  case OpenKan

object RealtimeSourceEventType:
  def toString(eventType: RealtimeSourceEventType): String =
    eventType.toString

  def fromString(value: String): RealtimeSourceEventType =
    RealtimeSourceEventType.valueOf(value)
