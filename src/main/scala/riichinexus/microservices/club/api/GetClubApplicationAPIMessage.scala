package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.ResolveRequestActorPrivateAPIMessage
import riichinexus.microservices.auth.api.`private`.CheckSuperAdminPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.{ResolvePlayerBoundClubIdsPrivateAPIMessage, ResolvePlayerByUserIdPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubMembershipApplication
import riichinexus.system.api.AuthorizationFailure
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.{ClubMembershipApplicantView, ClubMembershipApplicationView}
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.ReadWriter

/** 获取俱乐部申请详情。 */
final case class GetClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    for
      input <- IO.pure(resolveInput)
      actor <- resolveActor(context, input)
      view <- getApplicationView(context, input, actor)
    yield view

  private def resolveInput: GetClubApplicationInput =
    GetClubApplicationInput(
      clubId = ClubId(clubId),
      membershipId = MembershipApplicationId(membershipId),
      operatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_))
    )

  private def resolveActor(
      context: ApiPlanContext,
      input: GetClubApplicationInput
  ): IO[AccessPrincipalPrivateView] =
    ResolveRequestActorPrivateAPIMessage(None, input.operatorId).plan(context)

  private def getApplicationView(
      context: ApiPlanContext,
      input: GetClubApplicationInput,
      actor: AccessPrincipalPrivateView
  ): IO[ClubMembershipApplicationView] =
    for
      club <- IO.blocking(resolveClub(context.connection, input.clubId))
      application <- IO.blocking(resolveApplication(club, input))
      _ <- requireClubApplicationViewer(context, actor, club, application)
      view <- applicationView(context, club, application, actor)
    yield view

  private def resolveClub(connection: java.sql.Connection, clubId: ClubId): Club =
    ClubTable
      .findById(connection, clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

  private def resolveApplication(
      club: Club,
      input: GetClubApplicationInput
  ): ClubMembershipApplication =
    ClubFunctions.findApplication(club, input.membershipId).getOrElse(
      throw NoSuchElementException(
        s"Membership application ${input.membershipId.value} was not found in club ${input.clubId.value}"
      )
    )

  private def requireClubApplicationViewer(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      club: Club,
      application: ClubMembershipApplication
  ): IO[Unit] =
    if ClubAuthorization.canManageClubApplications(actor, club) then IO.unit
    else
      for
        canWithdraw <- canWithdrawClubApplication(context, actor, application)
        _ <-
          if canWithdraw then IO.unit
          else IO.raiseError(AuthorizationFailure(s"${actor.displayName} cannot view membership application ${application.id.value}"))
      yield ()

  private def applicationView(
      context: ApiPlanContext,
      club: Club,
      application: ClubMembershipApplication,
      actor: AccessPrincipalPrivateView
  ): IO[ClubMembershipApplicationView] =
    for
      applicantPlayer <- resolveApplicantPlayer(context, application)
      applicantClubIds <- applicantPlayer
        .map(player => ResolvePlayerBoundClubIdsPrivateAPIMessage(player.id).plan(context).map(_.map(_.value)))
        .getOrElse(IO.pure(Vector.empty))
      reviewedByDisplayName <- application.reviewedBy
        .map(playerId => ResolvePlayerPrivateAPIMessage(playerId).plan(context).map(_.map(_.nickname)))
        .getOrElse(IO.pure(None))
      canWithdraw <- canWithdrawClubApplication(context, actor, application)
    yield ClubMembershipApplicationView(
      applicationId = application.id.value,
      clubId = club.id.value,
      clubName = club.name,
      applicant = ClubMembershipApplicantView(
        playerId = applicantPlayer.map(_.id.value),
        displayName = application.displayName,
        playerStatus = applicantPlayer.map(_.status.toString),
        currentRank = applicantPlayer.map(_.currentRank),
        elo = applicantPlayer.map(_.elo),
        clubIds = applicantClubIds
      ),
      submittedAt = application.submittedAt.toString,
      message = application.message,
      status = ClubApplicationStatus.toString(application.status),
      reviewedBy = application.reviewedBy.map(_.value),
      reviewedByDisplayName = reviewedByDisplayName,
      reviewedAt = application.reviewedAt.map(_.toString),
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId,
      canReview = ClubMembershipApplicationFunctions.isPending(application) && ClubAuthorization.canManageClubApplications(actor, club),
      canWithdraw = ClubMembershipApplicationFunctions.isPending(application) && canWithdraw
    )

  private def canWithdrawClubApplication(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      application: ClubMembershipApplication
  ): IO[Boolean] =
    for
      isSuperAdmin <- CheckSuperAdminPrivateAPIMessage(actor).plan(context)
      canWithdraw <-
        if isSuperAdmin then IO.pure(true)
        else ownsClubApplication(context, actor, application)
    yield canWithdraw

  private def ownsClubApplication(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      application: ClubMembershipApplication
  ): IO[Boolean] =
    actor.playerId
      .map(playerId => ResolvePlayerPrivateAPIMessage(playerId).plan(context))
      .getOrElse(IO.pure(None))
      .map(_.exists { player =>
        application.playerId.contains(player.id) ||
          application.applicantUserId.contains(player.userId)
      })

  private def resolveApplicantPlayer(
      context: ApiPlanContext,
      application: ClubMembershipApplication
  ): IO[Option[riichinexus.microservices.player.objects.`private`.PlayerPrivateView]] =
    application.playerId match
      case Some(playerId) => ResolvePlayerPrivateAPIMessage(playerId).plan(context)
      case None =>
        application.applicantUserId
          .map(ResolvePlayerByUserIdPrivateAPIMessage(_).plan(context))
          .getOrElse(IO.pure(None))

  private final case class GetClubApplicationInput(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      operatorId: Option[PlayerId]
  )
