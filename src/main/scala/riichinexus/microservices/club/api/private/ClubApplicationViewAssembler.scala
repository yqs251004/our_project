package riichinexus.microservices.club.api.`private`

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
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.club.objects.apiTypes.{ClubMembershipApplicantView, ClubMembershipApplicationView}
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.RankSnapshotView

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
    actor.isSuperAdmin || ownsClubApplication(connection, module, actor, application)

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
        currentRank = applicantPlayer.map(player => RankSnapshotView.fromDomain(player.currentRank)),
        elo = applicantPlayer.map(_.elo),
        clubIds = applicantPlayer.map(_.boundClubIds.map(_.value)).getOrElse(Vector.empty)
      ),
      submittedAt = application.submittedAt.toString,
      message = application.message,
      status = application.status.toString,
      reviewedBy = application.reviewedBy.map(_.value),
      reviewedByDisplayName = application.reviewedBy.flatMap(playerId => GetPlayerAPIMessage.findPlayer(connection, playerId).map(_.nickname)),
      reviewedAt = application.reviewedAt.map(_.toString),
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId,
      canReview = application.isPending && canManageClubApplications(actor, club),
      canWithdraw = application.isPending && canWithdrawClubApplication(connection, module, actor, application)
    )
