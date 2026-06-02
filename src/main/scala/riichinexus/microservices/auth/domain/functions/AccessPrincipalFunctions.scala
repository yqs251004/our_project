package riichinexus.microservices.auth.domain.functions

import java.time.Instant

import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.{AccessPrincipal, GuestAccessSession}
import riichinexus.microservices.auth.objects.Role

object AccessPrincipalFunctions:
  def isGuest(principal: AccessPrincipal): Boolean =
    principal.playerId.isEmpty

  def isSuperAdmin(principal: AccessPrincipal): Boolean =
    principal.roleGrants.exists(_.role == Role.SuperAdmin)

  def hasRole(principal: AccessPrincipal, role: Role): Boolean =
    isSuperAdmin(principal) || principal.roleGrants.exists(_.role == role)

  def hasClubRole(principal: AccessPrincipal, role: Role, clubId: ClubId): Boolean =
    isSuperAdmin(principal) || principal.roleGrants.exists(grant => grant.role == role && grant.clubId.contains(clubId))

  def hasTournamentRole(principal: AccessPrincipal, role: Role, tournamentId: TournamentId): Boolean =
    isSuperAdmin(principal) || principal.roleGrants.exists(grant =>
      grant.role == role && grant.tournamentId.contains(tournamentId)
    )

  def guest(session: GuestAccessSession = GuestAccessSessionFunctions.ephemeral()): AccessPrincipal =
    AccessPrincipal(
      principalId = session.id.value,
      displayName = session.displayName,
      playerId = None,
      roleGrants = Vector(RoleGrantFunctions.guest(session.createdAt))
    )

  def system: AccessPrincipal =
    AccessPrincipal(
      principalId = "system-bootstrap",
      displayName = "system",
      playerId = None,
      roleGrants = Vector(RoleGrantFunctions.superAdmin(Instant.EPOCH, None))
    )
