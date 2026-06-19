package riichinexus.microservices.auth.domain.authorization

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
import riichinexus.microservices.auth.domain.model.RoleGrant
import riichinexus.microservices.auth.objects.Role

object RoleGrantFunctions:
  def appliesToClub(grant: RoleGrant, targetClubId: ClubId): Boolean =
    grant.role == Role.SuperAdmin || grant.clubId.contains(targetClubId)

  def appliesToTournament(grant: RoleGrant, targetTournamentId: TournamentId): Boolean =
    grant.role == Role.SuperAdmin || grant.tournamentId.contains(targetTournamentId)

  def guest(at: Instant = Instant.now()): RoleGrant =
    RoleGrant(Role.Guest, grantedAt = at)

  def registered(at: Instant): RoleGrant =
    RoleGrant(Role.RegisteredPlayer, grantedAt = at)

  def clubAdmin(
      clubId: ClubId,
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = Role.ClubAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy,
      clubId = Some(clubId)
    )

  def tournamentAdmin(
      tournamentId: TournamentId,
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = Role.TournamentAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy,
      tournamentId = Some(tournamentId)
    )

  def superAdmin(
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = Role.SuperAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy
    )

  def validate(grant: RoleGrant): Unit =
    require(
      grant.role match
        case Role.Guest | Role.RegisteredPlayer | Role.SuperAdmin =>
          grant.clubId.isEmpty && grant.tournamentId.isEmpty
        case Role.ClubAdmin =>
          grant.clubId.nonEmpty && grant.tournamentId.isEmpty
        case Role.TournamentAdmin =>
          grant.tournamentId.nonEmpty && grant.clubId.isEmpty,
      s"Invalid scope for role ${Role.toString(grant.role)}"
    )
