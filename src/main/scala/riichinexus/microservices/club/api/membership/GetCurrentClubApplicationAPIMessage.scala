package riichinexus.microservices.club.api.membership
import riichinexus.microservices.auth.api.authorization.`private`.ResolveRequestActorPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.CheckSuperAdminPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerBoundClubIdsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerByUserIdPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.membership.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membership.model.ClubMembershipApplication
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.membership.ClubApplicationStatus
import riichinexus.microservices.club.objects.membership.{ClubMembershipApplicantView, ClubMembershipApplicationView}
import riichinexus.microservices.club.tables.clubs.ClubTable
/** 获取当前玩家对指定俱乐部的申请。 */
final case class GetCurrentClubApplicationAPIMessage(
    clubId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubMembershipApplicationView]:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    val requestedClubId = ClubId(clubId)
    val operatorPlayerId = resolveOperatorId
    for
      actor <- resolveActor(context, operatorPlayerId)
      view <- getCurrentApplicationView(context, requestedClubId, actor)
    yield view

  private def resolveOperatorId: Option[PlayerId] =
    val operatorPlayerId = operatorId.filter(_.nonEmpty).map(PlayerId(_))
    if operatorPlayerId.isEmpty then
      throw IllegalArgumentException("operatorId is required")
    operatorPlayerId

  private def resolveActor(
      context: ApiPlanContext,
      operatorId: Option[PlayerId]
  ): IO[AccessPrincipalPrivateView] =
    ResolveRequestActorPrivateAPIMessage(None, operatorId).plan(context)

  private def getCurrentApplicationView(
      context: ApiPlanContext,
      clubId: ClubId,
      actor: AccessPrincipalPrivateView
  ): IO[ClubMembershipApplicationView] =
    for
      club <- loadClub(context, clubId)
      ownedApplications <- resolveOwnedPendingApplications(context, actor, club.membershipApplications)
      application = ownedApplications.maxByOption(_.submittedAt).getOrElse(throw NoSuchElementException("Resource not found"))
      view <- applicationView(context, club, application, actor)
    yield view

  private def loadClub(context: ApiPlanContext, clubId: ClubId): IO[Club] =
    IO.blocking {
      ClubTable
        .findById(context.connection, clubId)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    }

  private def resolveOwnedPendingApplications(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      applications: Vector[ClubMembershipApplication]
  ): IO[Vector[ClubMembershipApplication]] =
    applications.foldLeft(IO.pure(Vector.empty[ClubMembershipApplication])) { (previous, application) =>
      previous.flatMap { ownedApplications =>
        if ClubMembershipApplicationFunctions.isPending(application) then
          ownsClubApplication(context, actor, application).map {
            case true  => ownedApplications :+ application
            case false => ownedApplications
          }
        else IO.pure(ownedApplications)
      }
    }

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
