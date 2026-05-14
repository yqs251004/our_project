package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class ClubMembershipApplicationService(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dashboardRepository: DashboardRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def applyForMembership(
      clubId: ClubId,
      applicantUserId: Option[String],
      displayName: String,
      message: Option[String] = None,
      submittedAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.guest()
  ): Option[ClubMembershipApplication] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.SubmitClubApplication)

      clubRepository.findById(clubId).map { club =>
        ClubPolicySupport.ensureClubActive(club)
        require(
          club.recruitmentPolicy.applicationsOpen,
          s"Club ${clubId.value} is not currently accepting membership applications"
        )
        require(displayName.trim.nonEmpty, "Membership application display name cannot be empty")

        applicantUserId.foreach { userId =>
          if club.membershipApplications.exists(application =>
              application.applicantUserId.contains(userId) && application.isPending
            )
          then
            throw IllegalArgumentException(
              s"User $userId already has a pending application for club ${clubId.value}"
            )

          playerRepository.findByUserId(userId).foreach { existingPlayer =>
            if existingPlayer.boundClubIds.contains(clubId) then
              throw IllegalArgumentException(
                s"Player ${existingPlayer.id.value} is already a member of club ${clubId.value}"
              )
          }
        }

        val application = ClubMembershipApplication(
          id = IdGenerator.membershipApplicationId(),
          applicantUserId = applicantUserId,
          displayName = displayName,
          submittedAt = submittedAt,
          message = message
        )

        clubRepository.save(club.submitApplication(application))
        application
      }
    }

  def withdrawMembershipApplication(
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      actor: AccessPrincipal,
      withdrawnAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[ClubMembershipApplication] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.WithdrawClubApplication)

      clubRepository.findById(clubId).map { club =>
        ClubPolicySupport.ensureClubActive(club)

        val application = club
          .findApplication(applicationId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${applicationId.value} was not found in club ${clubId.value}"
            )
          )

        if !application.isPending then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} has already been reviewed"
          )

        requireApplicationOwnership(application, actor)
        val updatedApplication = application.withdraw(actor.principalId, withdrawnAt, note)
        clubRepository.save(club.reviewApplication(applicationId, _ => updatedApplication))
        updatedApplication
      }
    }

  def approveMembershipApplication(
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      approvedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireActivePlayer(player, s"Player ${playerId.value} cannot be approved into a club")
        ClubPolicySupport.requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        val application = club
          .findApplication(applicationId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${applicationId.value} was not found in club ${clubId.value}"
            )
          )

        if !application.isPending then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} has already been reviewed"
          )

        if club.members.contains(playerId) then
          throw IllegalArgumentException(
            s"Player ${playerId.value} is already a member of club ${clubId.value}"
          )

        if application.applicantUserId.exists(applicantUserId =>
            !applicantUserId.startsWith("guest:") && applicantUserId != player.userId
          )
        then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} does not belong to player ${playerId.value}"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        val updatedClub = club
          .reviewApplication(applicationId, _.approve(reviewer, approvedAt, note))
          .addMember(playerId)

        val savedPlayer = playerRepository.save(player.joinClub(clubId))
        ClubProjectionSupport.ensurePlayerDashboard(savedPlayer.id, dashboardRepository, approvedAt)
        clubRepository.save(
          ClubProjectionSupport.refreshClubProjection(
            updatedClub,
            playerRepository,
            globalDictionaryRepository,
            dashboardRepository,
            approvedAt
          )
        )
    }

  def rejectMembershipApplication(
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      actor: AccessPrincipal,
      rejectedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        val application = club
          .findApplication(applicationId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${applicationId.value} was not found in club ${clubId.value}"
            )
          )

        if !application.isPending then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} has already been reviewed"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        clubRepository.save(
          club.reviewApplication(applicationId, _.reject(reviewer, rejectedAt, note))
        )
      }
    }

  private def requireApplicationOwnership(
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): Unit =
    val ownedByGuest =
      actor.isGuest && application.applicantUserId.contains(s"guest:${actor.principalId}")

    val ownedByRegisteredPlayer =
      actor.playerId.flatMap(playerRepository.findById).exists(player =>
        application.applicantUserId.contains(player.userId)
      )

    if !ownedByGuest && !ownedByRegisteredPlayer && !actor.isSuperAdmin then
      throw AuthorizationFailure(
        s"${actor.displayName} cannot withdraw membership application ${application.id.value}"
      )
