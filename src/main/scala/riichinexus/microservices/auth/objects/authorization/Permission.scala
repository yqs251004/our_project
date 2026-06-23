package riichinexus.microservices.auth.objects.authorization

/** 授权策略识别的细粒度操作权限。
  *
  * 角色会被展开成这些权限，再由 API 或领域服务按具体场景校验，例如俱乐部管理、赛事编排、牌桌操作和平台审计。
  */
enum Permission:
  case ViewPublicSchedule
  case ViewClubDirectory
  case ViewPublicLeaderboard
  case ViewOwnDashboard
  case ViewClubDashboard
  case SubmitClubApplication
  case WithdrawClubApplication
  case ManageClubMembership
  case ManageClubOperations
  case SetClubTitle
  case AssignClubAdmin
  case SubmitTournamentLineup
  case ManageTournamentStages
  case ConfigureTournamentRules
  case ResetTableState
  case ManageTableSeatState
  case FileAppealTicket
  case ResolveAppeal
  case ManagePlatformOperations
  case BanRegisteredPlayer
  case DissolveClub
  case AssignTournamentAdmin
  case ViewAuditTrail
