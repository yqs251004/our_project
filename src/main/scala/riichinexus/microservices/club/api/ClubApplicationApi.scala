package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.club.api.responses.*
import riichinexus.microservices.club.api.requests.*
import riichinexus.microservices.club.objects.ClubApplicationQuery
import riichinexus.microservices.club.tables.ClubTables

object ClubApplicationApi:
  private type ActorResolver = (Option[GuestSessionId], Option[PlayerId]) => AccessPrincipal

  def listApplications(
      tables: ClubTables,
      views: ClubViewAssembler,
      requestActor: ActorResolver,
      containsIgnoreCase: (String, String) => Boolean,
      clubId: ClubId,
      query: ClubApplicationQuery
  ): Vector[ClubMembershipApplicationView] =
    val club = tables
      .findClub(clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    val actor = requestActor(
      query.guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_)),
      query.operatorId
    )
    views.requireClubApplicationManager(actor, club)
    club.membershipApplications
      .filter(application => query.status.forall(_ == application.status))
      .filter(application => query.applicantUserId.forall(value => application.applicantUserId.contains(value)))
      .filter(application => query.displayName.forall(containsIgnoreCase(application.displayName, _)))
      .sortBy(_.submittedAt)
      .map(application => views.buildClubMembershipApplicationView(club, application, actor))

  def currentApplication(
      tables: ClubTables,
      views: ClubViewAssembler,
      requestActor: ActorResolver,
      clubId: ClubId,
      guestSessionId: Option[GuestSessionId],
      operatorId: Option[PlayerId]
  ): Option[ClubMembershipApplicationView] =
    val club = tables
      .findClub(clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    if guestSessionId.isEmpty && operatorId.isEmpty then
      throw IllegalArgumentException("operatorId or guestSessionId is required")
    val actor = requestActor(guestSessionId, operatorId)
    club.membershipApplications
      .filter(application => application.isPending && views.ownsClubApplication(actor, application))
      .maxByOption(_.submittedAt)
      .map(application => views.buildClubMembershipApplicationView(club, application, actor))

  def applicationDetail(
      tables: ClubTables,
      views: ClubViewAssembler,
      requestActor: ActorResolver,
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      guestSessionId: Option[GuestSessionId],
      operatorId: Option[PlayerId]
  ): ClubMembershipApplicationView =
    val club = tables
      .findClub(clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    val actor = requestActor(guestSessionId, operatorId)
    val application = club.findApplication(applicationId).getOrElse(
      throw NoSuchElementException(s"Membership application ${applicationId.value} was not found in club ${clubId.value}")
    )
    views.requireClubApplicationViewer(actor, club, application)
    views.buildClubMembershipApplicationView(club, application, actor)

  def applyForMembership(
      tables: ClubTables,
      service: ClubApplicationService,
      requestActor: ActorResolver,
      clubId: ClubId,
      request: ClubMembershipApplicationRequest
  ): Option[ClubMembershipApplication] =
    val actor = requestActor(request.session, request.operator)
    val applicantUserId = request.applicantUserId
      .orElse(request.session.map(session => s"guest:${session.value}"))
      .orElse(request.operator.flatMap(playerId => tables.findPlayer(playerId).map(_.userId)))
    val displayName = request.session.map(_ => actor.displayName)
      .orElse(request.operator.flatMap(playerId => tables.findPlayer(playerId).map(_.nickname)))
      .getOrElse(request.displayName)
    service.applyForMembership(
      clubId = clubId,
      applicantUserId = applicantUserId,
      displayName = displayName,
      message = request.message,
      actor = actor
    )

  def withdrawMembershipApplication(
      service: ClubApplicationService,
      requestActor: ActorResolver,
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      request: WithdrawClubApplicationRequest
  ): Option[ClubMembershipApplication] =
    service.withdrawMembershipApplication(
      clubId = clubId,
      applicationId = applicationId,
      actor = requestActor(request.session, request.operator),
      note = request.note
    )

  def reviewMembershipApplication(
      tables: ClubTables,
      service: ClubApplicationService,
      views: ClubViewAssembler,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      request: ReviewClubApplicationRequest
  ): ClubMembershipApplicationView =
    val updatedClub =
      request.decision.trim.toLowerCase match
        case "approve" | "approved" =>
          val club = tables
            .findClub(clubId)
            .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
          val application = club.findApplication(applicationId).getOrElse(
            throw NoSuchElementException(s"Membership application ${applicationId.value} was not found in club ${clubId.value}")
          )
          val player = request.player
            .flatMap(playerId => tables.findPlayer(playerId))
            .orElse(
              application.applicantUserId
                .filterNot(_.startsWith("guest:"))
                .flatMap(tables.findPlayerByUserId)
            )
            .getOrElse(
              throw IllegalArgumentException(
                s"Membership application ${applicationId.value} requires playerId when approving a guest-origin application"
              )
            )
          service.approveMembershipApplication(
            clubId = clubId,
            applicationId = applicationId,
            playerId = player.id,
            actor = principalOf(request.operator),
            note = request.note
          )
        case "reject" | "rejected" =>
          service.rejectMembershipApplication(
            clubId = clubId,
            applicationId = applicationId,
            actor = principalOf(request.operator),
            note = request.note
          )
        case other =>
          throw IllegalArgumentException(
            s"Unsupported review decision '$other'. Supported decisions: approve, reject"
          )

    val reviewedClub = updatedClub.getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    val reviewedApplication = reviewedClub.findApplication(applicationId).getOrElse(
      throw NoSuchElementException(s"Membership application ${applicationId.value} was not found in club ${clubId.value}")
    )
    views.buildClubMembershipApplicationView(reviewedClub, reviewedApplication, principalOf(request.operator))

  def approveMembershipApplication(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      request: ApproveClubApplicationRequest
  ): Option[Club] =
    service.approveMembershipApplication(
      clubId = clubId,
      applicationId = applicationId,
      playerId = request.player,
      actor = principalOf(request.operator),
      note = request.note
    )

  def rejectMembershipApplication(
      service: ClubApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      request: RejectClubApplicationRequest
  ): Option[Club] =
    service.rejectMembershipApplication(
      clubId = clubId,
      applicationId = applicationId,
      actor = principalOf(request.operator),
      note = request.note
    )
