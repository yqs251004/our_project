package riichinexus.microservices.club.domain

import java.sql.Connection

import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.club.objects.{ClubMembershipApplicantView, ClubMembershipApplicationView}
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.objects.RankSnapshotView

object ClubApplicationViewAssembler:
  def canManageClubApplications(actor: AccessPrincipal, club: Club): Boolean =
    ClubAuthorization.canManageClubApplications(actor, club)

  def ownsClubApplication(
      connection: Connection,
      module: ClubModuleContext,
      actor: AccessPrincipal,
      application: ClubMembershipApplication
  ): Boolean =
    val ownedByGuest = actor.isGuest && application.applicantUserId.contains(s"guest:${actor.principalId}")
    val ownedByRegisteredPlayer =
      actor.playerId.flatMap(PlayerTable.findById(connection, _)).exists(player =>
        application.applicantUserId.contains(player.userId)
      )
    ownedByGuest || ownedByRegisteredPlayer

  def canWithdrawClubApplication(
      connection: Connection,
      module: ClubModuleContext,
      actor: AccessPrincipal,
      application: ClubMembershipApplication
  ): Boolean =
    actor.isSuperAdmin || ownsClubApplication(connection, module, actor, application)

  def applicationView(
      connection: Connection,
      module: ClubModuleContext,
      club: Club,
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): ClubMembershipApplicationView =
    val applicantPlayer = application.applicantUserId.flatMap(PlayerTable.findByUserId(connection, _))
    ClubMembershipApplicationView(
      applicationId = application.id.value,
      clubId = club.id.value,
      clubName = club.name,
      applicant = ClubMembershipApplicantView(
        playerId = applicantPlayer.map(_.id.value),
        applicantUserId = application.applicantUserId,
        displayName = application.displayName,
        playerStatus = applicantPlayer.map(_.status.toString),
        currentRank = applicantPlayer.map(player => RankSnapshotView.fromDomain(player.currentRank)),
        elo = applicantPlayer.map(_.elo),
        clubIds = applicantPlayer.map(_.boundClubIds.map(_.value)).getOrElse(Vector.empty)
      ),
      submittedAt = application.submittedAt.toString,
      message = application.message,
      status = application.status.toString,
      reviewedBy = application.reviewedBy.map(_.value),
      reviewedByDisplayName = application.reviewedBy.flatMap(playerId => PlayerTable.findById(connection, playerId).map(_.nickname)),
      reviewedAt = application.reviewedAt.map(_.toString),
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId,
      canReview = application.isPending && canManageClubApplications(actor, club),
      canWithdraw = application.isPending && canWithdrawClubApplication(connection, module, actor, application)
    )
