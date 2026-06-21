package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.ResolveRequestActorPrivateAPIMessage
import riichinexus.microservices.auth.api.`private`.CheckSuperAdminPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.{ResolvePlayerBoundClubIdsPrivateAPIMessage, ResolvePlayerByUserIdPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubMembershipApplication
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.{ClubMembershipApplicantView, ClubMembershipApplicationView}
import riichinexus.microservices.club.tables.clubs.ClubTable
/** 获取当前玩家对指定俱乐部的申请。 */
final case class GetCurrentClubApplicationAPIMessage(
    clubId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubMembershipApplicationView]:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    for
      input <- IO.pure(resolveInput)
      actor <- resolveActor(context, input)
      view <- getCurrentApplicationView(context, input, actor)
    yield view

  private def resolveInput: CurrentClubApplicationInput =
    val parsedOperatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_))
    if parsedOperatorId.isEmpty then
      throw IllegalArgumentException("operatorId is required")
    CurrentClubApplicationInput(
      clubId = ClubId(clubId),
      operatorId = parsedOperatorId
    )

  private def resolveActor(
      context: ApiPlanContext,
      input: CurrentClubApplicationInput
  ): IO[AccessPrincipalPrivateView] =
    ResolveRequestActorPrivateAPIMessage(None, input.operatorId).plan(context)

  private def getCurrentApplicationView(
      context: ApiPlanContext,
      input: CurrentClubApplicationInput,
      actor: AccessPrincipalPrivateView
  ): IO[ClubMembershipApplicationView] =
    for
      club <- loadClub(context, input.clubId)
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

  /** 查询当前访问者入会申请时使用的俱乐部和操作者键。 */
  private final case class CurrentClubApplicationInput(
      clubId: ClubId,
      operatorId: Option[PlayerId]
  )
