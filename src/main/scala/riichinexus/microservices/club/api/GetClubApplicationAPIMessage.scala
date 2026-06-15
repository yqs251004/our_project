package riichinexus.microservices.club.api
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
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
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.api.`private`.ClubApplicationViewAssembler
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubMembershipApplicationView
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.*

final case class GetClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    for
      input <- IO.blocking(resolveInput)
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
  ): IO[AccessPrincipal] =
    ResolveRequestActor(None, input.operatorId).plan(context)

  private def getApplicationView(
      context: ApiPlanContext,
      input: GetClubApplicationInput,
      actor: AccessPrincipal
  ): IO[ClubMembershipApplicationView] =
    for
      resolved <- IO.blocking {
        val club = resolveClub(context.connection, input.clubId)
        val application = resolveApplication(club, input)
        (club, application)
      }
      (club, application) = resolved
      _ <- requireClubApplicationViewer(context, actor, club, application)
      view <- ClubApplicationViewAssembler.applicationView(context, club, application, actor)
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
      actor: AccessPrincipal,
      club: Club,
      application: ClubMembershipApplication
  ): IO[Unit] =
    if ClubApplicationViewAssembler.canManageClubApplications(actor, club) then IO.unit
    else
      ClubApplicationViewAssembler.canWithdrawClubApplication(context, actor, application).flatMap { canWithdraw =>
        if canWithdraw then IO.unit
        else IO.raiseError(AuthorizationFailure(s"${actor.displayName} cannot view membership application ${application.id.value}"))
      }

  private final case class GetClubApplicationInput(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      operatorId: Option[PlayerId]
  )
