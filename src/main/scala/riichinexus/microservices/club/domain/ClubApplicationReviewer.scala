package riichinexus.microservices.club.domain

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationFailure

object ClubApplicationReviewer:
  def approve(
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
        club <- module.clubRepository.findById(parsedClubId)
        player <- module.playerRepository.findById(parsedPlayerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${parsedPlayerId.value} cannot be approved into a club")
        requireClubCapability(
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

        val savedPlayer = module.playerRepository.save(player.joinClub(parsedClubId))
        ClubProjectionRefresher.ensurePlayerDashboard(module, savedPlayer.id, approvedAt)
        module.clubRepository.save(ClubProjectionRefresher.refreshClubProjection(module, updatedClub, approvedAt))
    }

  def reject(
      module: ClubModuleContext,
      parsedClubId: ClubId,
      parsedMembershipId: MembershipApplicationId,
      actor: AccessPrincipal,
      note: Option[String],
      rejectedAt: Instant
  ): Option[Club] =
    module.transactionManager.inTransaction {
      module.clubRepository.findById(parsedClubId).map { club =>
        ensureClubActive(club)
        requireClubCapability(
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
        module.clubRepository.save(
          club.reviewApplication(parsedMembershipId, _.reject(reviewer, rejectedAt, note))
        )
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private def requireClubCapability(
      module: ClubModuleContext,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[String]
  ): Unit =
    val authorizationService = module.authorizationService
    val hasBasePermission = authorizationService.can(actor, permission, clubId = Some(club.id))
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
        delegatedPrivileges.exists(privilege => club.hasPrivilege(playerId, privilege))
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )
