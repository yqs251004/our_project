package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.*
import upickle.default.*

final case class GetClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    IO {
      val parsedClubId = ClubId(clubId)
      val parsedMembershipId = MembershipApplicationId(membershipId)
      val club = context.support.clubModule.tables
        .findClub(parsedClubId)
        .getOrElse(throw NoSuchElementException(s"Club ${parsedClubId.value} was not found"))
      val actor = context.support.requestActor(
        guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_)),
        operatorId.filter(_.nonEmpty).map(PlayerId(_))
      )
      val application = club.findApplication(parsedMembershipId).getOrElse(
        throw NoSuchElementException(s"Membership application ${parsedMembershipId.value} was not found in club ${parsedClubId.value}")
      )
      requireClubApplicationViewer(context, actor, club, application)
      buildClubMembershipApplicationView(context, club, application, actor)
    }

  private def requireClubApplicationViewer(
      context: ApiPlanContext,
      actor: AccessPrincipal,
      club: Club,
      application: ClubMembershipApplication
  ): Unit =
    if !canManageClubApplications(actor, club) && !canWithdrawClubApplication(context, actor, application) then
      throw AuthorizationFailure(s"${actor.displayName} cannot view membership application ${application.id.value}")

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
      applicationId = application.id,
      clubId = club.id,
      clubName = club.name,
      applicant = ClubMembershipApplicantView(
        playerId = applicantPlayer.map(_.id),
        applicantUserId = application.applicantUserId,
        displayName = application.displayName,
        playerStatus = applicantPlayer.map(_.status),
        currentRank = applicantPlayer.map(_.currentRank),
        elo = applicantPlayer.map(_.elo),
        clubIds = applicantPlayer.map(_.boundClubIds).getOrElse(Vector.empty)
      ),
      submittedAt = application.submittedAt,
      message = application.message,
      status = application.status,
      reviewedBy = application.reviewedBy,
      reviewedByDisplayName = application.reviewedBy.flatMap(playerId => tables.findPlayer(playerId).map(_.nickname)),
      reviewedAt = application.reviewedAt,
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId,
      canReview = application.isPending && canManageClubApplications(actor, club),
      canWithdraw = application.isPending && canWithdrawClubApplication(context, actor, application)
    )
