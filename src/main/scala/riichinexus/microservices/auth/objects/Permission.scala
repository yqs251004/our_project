package riichinexus.microservices.auth.objects

/** Permission 枚举权限 可使用的公开取值。 */

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
