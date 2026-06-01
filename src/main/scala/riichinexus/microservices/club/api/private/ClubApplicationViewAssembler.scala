package riichinexus.microservices.club.api.`private`

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.sql.Connection

import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.{ClubMembershipApplicantView, ClubMembershipApplicationView}
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.player.domain.functions.PlayerClubBindingFunctions

object ClubApplicationViewAssembler:
  def canManageClubApplications(actor: AccessPrincipal, club: Club): Boolean =
    ClubAuthorization.canManageClubApplications(actor, club)

  def ownsClubApplication(
      connection: Connection,
      module: ClubModuleContext,
      actor: AccessPrincipal,
      application: ClubMembershipApplication
  ): Boolean =
    val ownedByGuest = AccessPrincipalFunctions.isGuest(actor) && application.applicantUserId.contains(s"guest:${actor.principalId}")
    val ownedByRegisteredPlayer =
      actor.playerId.flatMap(GetPlayerAPIMessage.findPlayer(connection, _)).exists(player =>
        application.applicantUserId.contains(player.userId)
      )
    ownedByGuest || ownedByRegisteredPlayer

  def canWithdrawClubApplication(
      connection: Connection,
      module: ClubModuleContext,
      actor: AccessPrincipal,
      application: ClubMembershipApplication
  ): Boolean =
    AccessPrincipalFunctions.isSuperAdmin(actor) || ownsClubApplication(connection, module, actor, application)

  def applicationView(
      connection: Connection,
      module: ClubModuleContext,
      club: Club,
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): ClubMembershipApplicationView =
    val applicantPlayer = application.applicantUserId.flatMap(CreatePlayerAPIMessage.findPlayerByUserId(connection, _))
    ClubMembershipApplicationView(
      applicationId = application.id.value,
      clubId = club.id.value,
      clubName = club.name,
      applicant = ClubMembershipApplicantView(
        playerId = applicantPlayer.map(_.id.value),
        applicantUserId = application.applicantUserId,
        displayName = application.displayName,
        playerStatus = applicantPlayer.map(_.status.toString),
        currentRank = applicantPlayer.map(_.currentRank),
        elo = applicantPlayer.map(_.elo),
        clubIds = applicantPlayer.map(player => PlayerClubBindingFunctions.boundClubIds(player).map(_.value)).getOrElse(Vector.empty)
      ),
      submittedAt = application.submittedAt.toString,
      message = application.message,
      status = ClubApplicationStatus.toString(application.status),
      reviewedBy = application.reviewedBy.map(_.value),
      reviewedByDisplayName = application.reviewedBy.flatMap(playerId => GetPlayerAPIMessage.findPlayer(connection, playerId).map(_.nickname)),
      reviewedAt = application.reviewedAt.map(_.toString),
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId,
      canReview = ClubMembershipApplicationFunctions.isPending(application) && canManageClubApplications(actor, club),
      canWithdraw = ClubMembershipApplicationFunctions.isPending(application) && canWithdrawClubApplication(connection, module, actor, application)
    )
