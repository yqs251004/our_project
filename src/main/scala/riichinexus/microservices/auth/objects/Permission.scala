package riichinexus.microservices.auth.objects

import riichinexus.domain.model.{Permission as DomainPermission}
import upickle.default.*

enum Permission derives CanEqual:
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

  def toDomain: DomainPermission =
    DomainPermission.valueOf(toString)

object Permission:
  given ReadWriter[Permission] = readwriter[String].bimap(_.toString, Permission.valueOf)

  def fromDomain(permission: DomainPermission): Permission =
    Permission.valueOf(permission.toString)
