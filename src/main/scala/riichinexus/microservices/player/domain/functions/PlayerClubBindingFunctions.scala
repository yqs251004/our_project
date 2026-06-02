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
import riichinexus.microservices.player.domain.Player

object PlayerClubBindingFunctions:
  def boundClubIds(player: Player): Vector[ClubId] =
    (player.clubId.toVector ++ player.affiliatedClubIds).distinct

  def joinClub(player: Player, newClubId: ClubId): Player =
    val updatedBoundClubs = (boundClubIds(player) :+ newClubId).distinct
    val nextPrimaryClubId = player.clubId.orElse(Some(newClubId))
    player.copy(
      clubId = nextPrimaryClubId,
      affiliatedClubIds = updatedBoundClubs.filterNot(nextPrimaryClubId.contains)
    )

  def leaveClub(player: Player, existingClubId: ClubId): Player =
    val remaining = boundClubIds(player).filterNot(_ == existingClubId)
    player.copy(
      clubId = remaining.headOption,
      affiliatedClubIds = remaining.drop(1)
    )

  def leavePrimaryClub(player: Player): Player =
    player.clubId match
      case Some(primaryClubId) => leaveClub(player, primaryClubId)
      case None                => player.copy(affiliatedClubIds = Vector.empty)
