package riichinexus.microservices.player.domain.functions

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
import riichinexus.microservices.player.domain.Player

private[player] object PlayerRoleFunctions:
  def effectiveRoleGrants(player: Player): Vector[RoleGrant] =
    if player.roleGrants.exists(_.role == Role.RegisteredPlayer) then player.roleGrants
    else RoleGrant(Role.RegisteredPlayer, grantedAt = player.registeredAt) +: player.roleGrants

  def grantRole(player: Player, grant: RoleGrant): Player =
    val normalized = player.roleGrants.filterNot(existing =>
      existing.role == grant.role &&
        existing.clubId == grant.clubId &&
        existing.tournamentId == grant.tournamentId
    )
    player.copy(roleGrants = (normalized :+ grant).sortBy(_.grantedAt.toEpochMilli))

  def revokeClubAdmin(player: Player, clubId: ClubId): Player =
    player.copy(roleGrants = player.roleGrants.filterNot(grant =>
      grant.role == Role.ClubAdmin && grant.clubId.contains(clubId)
    ))

  def revokeTournamentAdmin(player: Player, tournamentId: TournamentId): Player =
    player.copy(roleGrants = player.roleGrants.filterNot(grant =>
      grant.role == Role.TournamentAdmin && grant.tournamentId.contains(tournamentId)
    ))
