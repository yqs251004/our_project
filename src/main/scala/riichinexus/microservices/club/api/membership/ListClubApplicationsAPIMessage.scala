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
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.membership.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membership.model.ClubMembershipApplication

import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.membership.ClubApplicationStatus
import riichinexus.microservices.club.objects.membership.{ClubMembershipApplicantView, ClubMembershipApplicationView}
import riichinexus.microservices.club.objects.membership.apiTypes.ClubApplicationListQuery
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出俱乐部申请。 */
final case class ListClubApplicationsAPIMessage(
    clubId: String,
    query: ClubApplicationListQuery
) extends APIMessage[PagedResponse[ClubMembershipApplicationView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubMembershipApplicationView]] =
    val requestedClubId = ClubId(clubId)
    val operatorPlayerId = Option(query.operatorId).filter(_.nonEmpty).map(PlayerId(_))
    val requestedPlayerId = query.playerId.filter(_.nonEmpty).map(PlayerId(_))
    val displayNameFilter = query.displayName.filter(_.nonEmpty)
    val resolvedLimit = query.limit.getOrElse(20)
    val resolvedOffset = query.offset.getOrElse(0)
    val appliedFilters = applicationListFilters
    for
      actor <- ResolveRequestActorPrivateAPIMessage(guestSessionId = None, operatorId = operatorPlayerId).plan(context)
      page <- listApplications(
        context,
        requestedClubId,
        query.status,
        requestedPlayerId,
        displayNameFilter,
        resolvedLimit,
        resolvedOffset,
        appliedFilters,
        actor
      )
    yield page

  private def applicationListFilters: Map[String, String] =
    Vector(
      Option(query.operatorId).filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.OperatorId) -> _),
      query.status.map(status => QueryFilterField.toString(QueryFilterField.Status) -> ClubApplicationStatus.toString(status)),
      query.playerId.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.PlayerId) -> _),
      query.displayName.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.DisplayName) -> _)
    ).flatten.toMap

  private def listApplications(
      context: ApiPlanContext,
      clubId: ClubId,
      status: Option[ClubApplicationStatus],
      playerId: Option[PlayerId],
      displayName: Option[String],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String],
      actor: AccessPrincipalPrivateView
  ): IO[PagedResponse[ClubMembershipApplicationView]] =
    for
      club <- loadClub(context, clubId)
      _ <- IO.blocking(ClubAuthorization.requireClubApplicationManager(actor, club))
      applications <- IO.blocking(filterApplications(club, status, playerId, displayName))
      views <- applications.foldLeft(IO.pure(Vector.empty[ClubMembershipApplicationView])) { (previous, application) =>
        previous.flatMap(views => applicationView(context, club, application, actor).map(view => views :+ view))
      }
      page = pagedResponse(views, limit, offset, appliedFilters)
    yield page

  private def loadClub(context: ApiPlanContext, clubId: ClubId): IO[Club] =
    IO.blocking {
      ClubTable
        .findById(context.connection, clubId)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    }

  private def filterApplications(
      club: Club,
      status: Option[ClubApplicationStatus],
      playerId: Option[PlayerId],
      displayName: Option[String]
  ): Vector[ClubMembershipApplication] =
    club.membershipApplications
      .filter(application => status.forall(_ == application.status))
      .filter(application => playerId.forall(application.playerId.contains))
      .filter(application => displayName.forall(riichinexus.system.TextSearch.containsIgnoreCase(application.displayName, _)))
      .sortBy(_.submittedAt)

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

  private def pagedResponse(
      applications: Vector[ClubMembershipApplicationView],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  ): PagedResponse[ClubMembershipApplicationView] =
    require(limit > 0, "Input field limit must be positive")
    require(offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(limit, 100)
    val page = applications.slice(offset, offset + boundedLimit)
    PagedResponse(
      items = page,
      total = applications.size,
      limit = boundedLimit,
      offset = offset,
      hasMore = offset + page.size < applications.size,
      appliedFilters = appliedFilters
    )
