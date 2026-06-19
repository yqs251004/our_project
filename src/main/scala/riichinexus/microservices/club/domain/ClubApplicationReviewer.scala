package riichinexus.microservices.club.domain
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.player.api.`private`.*

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
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
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}

object ClubApplicationReviewer:
  def approve(
      context: ApiPlanContext,
      parsedClubId: ClubId,
      parsedMembershipId: MembershipApplicationId,
      parsedPlayerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String],
      approvedAt: Instant
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, parsedClubId))
      player <- ResolvePlayerPrivateAPIMessage(parsedPlayerId).plan(context)
      savedClub <- (club, player) match
        case (Some(club), Some(player)) =>
          ClubAuthorization.ensureClubActive(club)
          requireActivePlayer(player, s"Player ${parsedPlayerId.value} cannot be approved into a club")
          ClubAuthorization.requireClubCapability(          actor = actor,
            club = club,
            permission = Permission.ManageClubMembership,
            delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
          )

          val application = ClubFunctions.findApplication(club, parsedMembershipId)
            .getOrElse(
              throw NoSuchElementException(
                s"Membership application ${parsedMembershipId.value} was not found in club ${parsedClubId.value}"
              )
            )

          if !ClubMembershipApplicationFunctions.isPending(application) then
            throw IllegalArgumentException(
              s"Membership application ${parsedMembershipId.value} has already been reviewed"
            )

          if club.members.contains(parsedPlayerId) then
            throw IllegalArgumentException(
              s"Player ${parsedPlayerId.value} is already a member of club ${parsedClubId.value}"
            )

          if !application.playerId.contains(parsedPlayerId) &&
              !application.applicantUserId.contains(player.userId)
          then
            throw IllegalArgumentException(
              s"Membership application ${parsedMembershipId.value} does not belong to player ${parsedPlayerId.value}"
            )

          val reviewer = actor.playerId.getOrElse(club.creator)
          val updatedClub = ClubFunctions.addMember(
            ClubFunctions.reviewApplication(club, parsedMembershipId, ClubMembershipApplicationFunctions.approve(_, reviewer, approvedAt, note)),
            parsedPlayerId
          )

          for
            savedPlayer <- JoinPlayerClubPrivateAPIMessage(parsedPlayerId, parsedClubId)
              .plan(context)
              .map(_.getOrElse(throw NoSuchElementException(s"Player ${parsedPlayerId.value} was not found")))
            _ <- ClubProjectionRefresher.ensurePlayerDashboard(context, savedPlayer.id, approvedAt)
            refreshedClub <- ClubProjectionRefresher.refreshClubProjection(context, updatedClub, approvedAt)
            savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, refreshedClub))
          yield Some(savedClub)
        case _ =>
          IO.pure(None)
    yield savedClub

  def reject(
      context: ApiPlanContext,
      parsedClubId: ClubId,
      parsedMembershipId: MembershipApplicationId,
      actor: AccessPrincipal,
      note: Option[String],
      rejectedAt: Instant
  ): IO[Option[Club]] =
    val connection = context.connection
    IO.blocking {
      riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, parsedClubId).map { club =>
        ClubAuthorization.ensureClubActive(club)
        ClubAuthorization.requireClubCapability(          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
        )

        val application = ClubFunctions.findApplication(club, parsedMembershipId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${parsedMembershipId.value} was not found in club ${parsedClubId.value}"
            )
          )

        if !ClubMembershipApplicationFunctions.isPending(application) then
          throw IllegalArgumentException(
            s"Membership application ${parsedMembershipId.value} has already been reviewed"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, 
          ClubFunctions.reviewApplication(club, parsedMembershipId, ClubMembershipApplicationFunctions.reject(_, reviewer, rejectedAt, note))
        )
      }
    }

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)
