package riichinexus.microservices.club.api
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

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
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.api.`private`.ClubApplicationViewAssembler
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubMembershipApplicationView
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.*

final case class GetCurrentClubApplicationAPIMessage(
    clubId: String,
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    for
      input <- IO.blocking(resolveInput)
      actor <- resolveActor(context, input)
      view <- IO.blocking(getCurrentApplicationView(context, input, actor))
    yield view

  private def resolveInput: CurrentClubApplicationInput =
    val parsedGuestSessionId = guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_))
    val parsedOperatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_))
    if parsedGuestSessionId.isEmpty && parsedOperatorId.isEmpty then
      throw IllegalArgumentException("operatorId or guestSessionId is required")
    CurrentClubApplicationInput(
      clubId = ClubId(clubId),
      operatorId = parsedOperatorId,
      guestSessionId = parsedGuestSessionId
    )

  private def resolveActor(
      context: ApiPlanContext,
      input: CurrentClubApplicationInput
  ): IO[AccessPrincipal] =
    ResolveRequestActor(input.guestSessionId, input.operatorId).plan(context)

  private def getCurrentApplicationView(
      context: ApiPlanContext,
      input: CurrentClubApplicationInput,
      actor: AccessPrincipal
  ): ClubMembershipApplicationView =
    val club = ClubTable
      .findById(context.connection, input.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${input.clubId.value} was not found"))
    val application = club.membershipApplications
      .filter(application => ClubMembershipApplicationFunctions.isPending(application) && ClubApplicationViewAssembler.ownsClubApplication(context.connection, actor, application))
      .maxByOption(_.submittedAt)
      .getOrElse(throw NoSuchElementException("Resource not found"))
    ClubApplicationViewAssembler.applicationView(context.connection, club, application, actor)

  private final case class CurrentClubApplicationInput(
      clubId: ClubId,
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  )
