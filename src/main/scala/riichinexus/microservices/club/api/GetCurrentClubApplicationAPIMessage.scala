package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.{Club as _, ClubRelation as _, ClubMembershipApplication as _, ClubPrivilegeDefinition as _, ClubMemberPrivilegeSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.RankSnapshotView
import upickle.default.*

final case class GetCurrentClubApplicationAPIMessage(
    clubId: String,
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    IO {
      val parsedClubId = ClubId(clubId)
      val parsedGuestSessionId = guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_))
      val parsedOperatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_))
      val club = context.support.clubModule.tables
        .findClub(parsedClubId)
        .getOrElse(throw NoSuchElementException(s"Club ${parsedClubId.value} was not found"))
      if parsedGuestSessionId.isEmpty && parsedOperatorId.isEmpty then
        throw IllegalArgumentException("operatorId or guestSessionId is required")
      val actor = context.support.requestActor(parsedGuestSessionId, parsedOperatorId)
      club.membershipApplications
        .filter(application => application.isPending && ownsClubApplication(context, actor, application))
        .maxByOption(_.submittedAt)
        .map(application => buildClubMembershipApplicationView(context, club, application, actor))
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }

  private def canManageClubApplications(actor: AccessPrincipal, club: Club): Boolean =
    actor.isSuperAdmin || actor.playerId.exists(playerId =>
      club.admins.contains(playerId) || club.hasPrivilege(playerId, ClubPrivilege.ApproveRoster)
    )

  private def ownsClubApplication(
      context: ApiPlanContext,
      actor: AccessPrincipal,
      application: ClubMembershipApplication
  ): Boolean =
    val ownedByGuest = actor.isGuest && application.applicantUserId.contains(s"guest:${actor.principalId}")
    val ownedByRegisteredPlayer =
      actor.playerId.flatMap(context.support.clubModule.tables.findPlayer).exists(player =>
        application.applicantUserId.contains(player.userId)
      )
    ownedByGuest || ownedByRegisteredPlayer

  private def canWithdrawClubApplication(
      context: ApiPlanContext,
      actor: AccessPrincipal,
      application: ClubMembershipApplication
  ): Boolean =
    actor.isSuperAdmin || ownsClubApplication(context, actor, application)

  private def buildClubMembershipApplicationView(
      context: ApiPlanContext,
      club: Club,
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): ClubMembershipApplicationView =
    val tables = context.support.clubModule.tables
    val applicantPlayer = application.applicantUserId.flatMap(tables.findPlayerByUserId)
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
      reviewedByDisplayName = application.reviewedBy.flatMap(playerId => tables.findPlayer(playerId).map(_.nickname)),
      reviewedAt = application.reviewedAt.map(_.toString),
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId,
      canReview = application.isPending && canManageClubApplications(actor, club),
      canWithdraw = application.isPending && canWithdrawClubApplication(context, actor, application)
    )
