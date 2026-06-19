package riichinexus.microservices.club.api.`private`
import riichinexus.microservices.player.api.`private`.*

import riichinexus.microservices.auth.domain.authorization.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext

import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.{ClubMembershipApplicantView, ClubMembershipApplicationView}
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}

object ClubApplicationViewAssembler:
  def canManageClubApplications(actor: AccessPrincipal, club: Club): Boolean =
    ClubAuthorization.canManageClubApplications(actor, club)

  def ownsClubApplication(
      context: ApiPlanContext,
      actor: AccessPrincipal,
      application: ClubMembershipApplication
  ): IO[Boolean] =
    actor.playerId
      .map(playerId => ResolvePlayerPrivateAPIMessage(playerId).plan(context))
      .getOrElse(IO.pure(None))
      .map(_.exists { player =>
        application.playerId.contains(player.id) ||
          application.applicantUserId.contains(player.userId)
      })

  def canWithdrawClubApplication(
      context: ApiPlanContext,
      actor: AccessPrincipal,
      application: ClubMembershipApplication
  ): IO[Boolean] =
    if AccessPrincipalFunctions.isSuperAdmin(actor) then IO.pure(true)
    else ownsClubApplication(context, actor, application)

  def applicationView(
      context: ApiPlanContext,
      club: Club,
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): IO[ClubMembershipApplicationView] =
    for
      applicantPlayer <- resolveApplicantPlayer(context, application)
      reviewedByDisplayName <- application.reviewedBy
        .map(playerId => ResolvePlayerPrivateAPIMessage(playerId).plan(context).map(_.map(_.nickname)))
        .getOrElse(IO.pure(None))
      canWithdraw <- canWithdrawClubApplication(context, actor, application)
    yield
      ClubMembershipApplicationView(
        applicationId = application.id.value,
        clubId = club.id.value,
        clubName = club.name,
        applicant = ClubMembershipApplicantView(
          playerId = applicantPlayer.map(_.id.value),
          displayName = application.displayName,
          playerStatus = applicantPlayer.map(_.status.toString),
          currentRank = applicantPlayer.map(_.currentRank),
          elo = applicantPlayer.map(_.elo),
          clubIds = applicantPlayer.map(boundClubIds(_).map(_.value)).getOrElse(Vector.empty)
        ),
        submittedAt = application.submittedAt.toString,
        message = application.message,
        status = ClubApplicationStatus.toString(application.status),
        reviewedBy = application.reviewedBy.map(_.value),
        reviewedByDisplayName = reviewedByDisplayName,
        reviewedAt = application.reviewedAt.map(_.toString),
        reviewNote = application.reviewNote,
        withdrawnByPrincipalId = application.withdrawnByPrincipalId,
        canReview = ClubMembershipApplicationFunctions.isPending(application) && canManageClubApplications(actor, club),
        canWithdraw = ClubMembershipApplicationFunctions.isPending(application) && canWithdraw
      )

  private def resolveApplicantPlayer(
      context: ApiPlanContext,
      application: ClubMembershipApplication
  ): IO[Option[riichinexus.microservices.player.domain.Player]] =
    application.playerId match
      case Some(playerId) => ResolvePlayerPrivateAPIMessage(playerId).plan(context)
      case None =>
        application.applicantUserId
          .map(ResolvePlayerByUserIdPrivateAPIMessage(_).plan(context))
          .getOrElse(IO.pure(None))

  private def boundClubIds(player: riichinexus.microservices.player.domain.Player): Vector[ClubId] =
    (player.clubId.toVector ++ player.affiliatedClubIds).distinct
