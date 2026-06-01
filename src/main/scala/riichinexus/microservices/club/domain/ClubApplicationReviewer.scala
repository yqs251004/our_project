package riichinexus.microservices.club.domain

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
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
import riichinexus.microservices.player.domain.functions.PlayerClubBindingFunctions
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}

object ClubApplicationReviewer:
  def approve(
      connection: Connection,
      module: ClubModuleContext,
      parsedClubId: ClubId,
      parsedMembershipId: MembershipApplicationId,
      parsedPlayerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String],
      approvedAt: Instant
  ): Option[Club] =
    module.transactionManager.inTransaction {
      for
        club <- riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, parsedClubId)
        player <- GetPlayerAPIMessage.findPlayer(connection, parsedPlayerId)
      yield
        ClubAuthorization.ensureClubActive(club)
        requireActivePlayer(player, s"Player ${parsedPlayerId.value} cannot be approved into a club")
        ClubAuthorization.requireClubCapability(
          module = module,
          actor = actor,
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

        if application.applicantUserId.exists(applicantUserId =>
            !applicantUserId.startsWith("guest:") && applicantUserId != player.userId
          )
        then
          throw IllegalArgumentException(
            s"Membership application ${parsedMembershipId.value} does not belong to player ${parsedPlayerId.value}"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        val updatedClub = ClubFunctions.addMember(
          ClubFunctions.reviewApplication(club, parsedMembershipId, ClubMembershipApplicationFunctions.approve(_, reviewer, approvedAt, note)),
          parsedPlayerId
        )

        val savedPlayer = CreatePlayerAPIMessage.persistPlayer(connection, PlayerClubBindingFunctions.joinClub(player, parsedClubId))
        ClubProjectionRefresher.ensurePlayerDashboard(connection, savedPlayer.id, approvedAt)
        riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubProjectionRefresher.refreshClubProjection(connection, module, updatedClub, approvedAt))
    }

  def reject(
      connection: Connection,
      module: ClubModuleContext,
      parsedClubId: ClubId,
      parsedMembershipId: MembershipApplicationId,
      actor: AccessPrincipal,
      note: Option[String],
      rejectedAt: Instant
  ): Option[Club] =
    module.transactionManager.inTransaction {
      riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, parsedClubId).map { club =>
        ClubAuthorization.ensureClubActive(club)
        ClubAuthorization.requireClubCapability(
          module = module,
          actor = actor,
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
