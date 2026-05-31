package riichinexus.microservices.club.domain

import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
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
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        val application = club
          .findApplication(parsedMembershipId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${parsedMembershipId.value} was not found in club ${parsedClubId.value}"
            )
          )

        if !application.isPending then
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
        val updatedClub = club
          .reviewApplication(parsedMembershipId, _.approve(reviewer, approvedAt, note))
          .addMember(parsedPlayerId)

        val savedPlayer = CreatePlayerAPIMessage.persistPlayer(connection, player.joinClub(parsedClubId))
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
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        val application = club
          .findApplication(parsedMembershipId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${parsedMembershipId.value} was not found in club ${parsedClubId.value}"
            )
          )

        if !application.isPending then
          throw IllegalArgumentException(
            s"Membership application ${parsedMembershipId.value} has already been reviewed"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, 
          club.reviewApplication(parsedMembershipId, _.reject(reviewer, rejectedAt, note))
        )
      }
    }

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)
