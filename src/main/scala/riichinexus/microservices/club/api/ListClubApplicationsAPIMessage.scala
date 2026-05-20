package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListClubApplicationsAPIMessage(
    clubId: String,
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None,
    status: Option[String] = None,
    applicantUserId: Option[String] = None,
    displayName: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[ClubMembershipApplicationView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubMembershipApplicationView]] =
    IO {
      val parsedOperatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_))
      val parsedGuestSessionId = guestSessionId.filter(_.nonEmpty)
      val parsedStatus = status.filter(_.nonEmpty).map(context.support.parseEnum("status", _)(ClubMembershipApplicationStatus.valueOf))
      val parsedApplicantUserId = applicantUserId.filter(_.nonEmpty)
      val parsedDisplayName = displayName.filter(_.nonEmpty)
      val parsedClubId = ClubId(clubId)
      val club = context.support.clubModule.tables
        .findClub(parsedClubId)
        .getOrElse(throw NoSuchElementException(s"Club ${parsedClubId.value} was not found"))
      val actor = context.support.requestActor(
        parsedGuestSessionId.map(GuestSessionId(_)),
        parsedOperatorId
      )
      requireClubApplicationManager(actor, club)
      val applications = club.membershipApplications
        .filter(application => parsedStatus.forall(_ == application.status))
        .filter(application => parsedApplicantUserId.forall(value => application.applicantUserId.contains(value)))
        .filter(application => parsedDisplayName.forall(context.support.containsIgnoreCase(application.displayName, _)))
        .sortBy(_.submittedAt)
        .map(application => buildClubMembershipApplicationView(context, club, application, actor))
      val resolvedLimit = limit.getOrElse(20)
      val resolvedOffset = offset.getOrElse(0)
      require(resolvedLimit > 0, "Input field limit must be positive")
      require(resolvedOffset >= 0, "Input field offset must be non-negative")
      val boundedLimit = math.min(resolvedLimit, 100)
      val page = applications.slice(resolvedOffset, resolvedOffset + boundedLimit)
      PagedResponse(
        items = page,
        total = applications.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < applications.size,
        appliedFilters = Vector(
          operatorId.filter(_.nonEmpty).map("operatorId" -> _),
          guestSessionId.filter(_.nonEmpty).map("guestSessionId" -> _),
          status.filter(_.nonEmpty).map("status" -> _),
          applicantUserId.filter(_.nonEmpty).map("applicantUserId" -> _),
          displayName.filter(_.nonEmpty).map("displayName" -> _)
        ).flatten.toMap
      )
    }

  private def requireClubApplicationManager(actor: AccessPrincipal, club: Club): Unit =
    if !canManageClubApplications(actor, club) then
      throw AuthorizationFailure(s"${actor.displayName} cannot manage membership applications for club ${club.id.value}")

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
