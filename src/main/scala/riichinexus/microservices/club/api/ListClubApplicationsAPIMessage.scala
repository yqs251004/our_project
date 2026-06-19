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
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubMembershipApplication

import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.{ClubMembershipApplicantView, ClubMembershipApplicationView}
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubApplicationListQuery
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.system.objects.PagedResponse
import upickle.default.ReadWriter

/** 列出俱乐部申请。 */
final case class ListClubApplicationsAPIMessage(
    clubId: String,
    query: ClubApplicationListQuery
) extends APIMessage[PagedResponse[ClubMembershipApplicationView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubMembershipApplicationView]] =
    for
      resolved <- IO.pure(resolveQuery)
      actor <- ResolveRequestActorPrivateAPIMessage(guestSessionId = None, operatorId = resolved.operatorId).plan(context)
      page <- listApplications(context, resolved, actor)
    yield page

  private def resolveQuery: ResolvedClubApplicationListQuery =
    ResolvedClubApplicationListQuery(
      clubId = ClubId(clubId),
      operatorId = Option(query.operatorId).filter(_.nonEmpty).map(PlayerId(_)),
      status = query.status,
      playerId = query.playerId.filter(_.nonEmpty).map(PlayerId(_)),
      displayName = query.displayName.filter(_.nonEmpty),
      limit = query.limit.getOrElse(20),
      offset = query.offset.getOrElse(0),
      appliedFilters = Vector(
        Option(query.operatorId).filter(_.nonEmpty).map("operatorId" -> _),
        query.status.map(status => "status" -> ClubApplicationStatus.toString(status)),
        query.playerId.filter(_.nonEmpty).map("playerId" -> _),
        query.displayName.filter(_.nonEmpty).map("displayName" -> _)
      ).flatten.toMap
    )

  private def listApplications(
      context: ApiPlanContext,
      query: ResolvedClubApplicationListQuery,
      actor: AccessPrincipalPrivateView
  ): IO[PagedResponse[ClubMembershipApplicationView]] =
    for
      club <- loadClub(context, query.clubId)
      _ <- IO.blocking(ClubAuthorization.requireClubApplicationManager(actor, club))
      applications <- IO.blocking(filterApplications(club, query))
      views <- applications.foldLeft(IO.pure(Vector.empty[ClubMembershipApplicationView])) { (previous, application) =>
        previous.flatMap(views => applicationView(context, club, application, actor).map(view => views :+ view))
      }
      page = pagedResponse(views, query)
    yield page

  private def loadClub(context: ApiPlanContext, clubId: ClubId): IO[Club] =
    IO.blocking {
      ClubTable
        .findById(context.connection, clubId)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    }

  private def filterApplications(
      club: Club,
      query: ResolvedClubApplicationListQuery
  ): Vector[ClubMembershipApplication] =
    club.membershipApplications
      .filter(application => query.status.forall(_ == application.status))
      .filter(application => query.playerId.forall(application.playerId.contains))
      .filter(application => query.displayName.forall(riichinexus.system.TextSearch.containsIgnoreCase(application.displayName, _)))
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
      query: ResolvedClubApplicationListQuery
  ): PagedResponse[ClubMembershipApplicationView] =
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val page = applications.slice(query.offset, query.offset + boundedLimit)
    PagedResponse(
      items = page,
      total = applications.size,
      limit = boundedLimit,
      offset = query.offset,
      hasMore = query.offset + page.size < applications.size,
      appliedFilters = query.appliedFilters
    )

  private final case class ResolvedClubApplicationListQuery(
      clubId: ClubId,
      operatorId: Option[PlayerId],
      status: Option[ClubApplicationStatus],
      playerId: Option[PlayerId],
      displayName: Option[String],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
