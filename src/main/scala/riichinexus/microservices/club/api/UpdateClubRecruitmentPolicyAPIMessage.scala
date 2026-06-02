package riichinexus.microservices.club.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
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
import riichinexus.microservices.auth.domain.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.UpdateClubRecruitmentPolicyRequest
import upickle.default.*

final case class UpdateClubRecruitmentPolicyAPIMessage(
    clubId: String,
    request: UpdateClubRecruitmentPolicyRequest
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(request.operatorId)).resolve(context.connection))
      occurredAt <- IO.realTimeInstant
      command = UpdateClubRecruitmentPolicyCommand(
        clubId = ClubId(clubId),
        actor = actor,
        policy = recruitmentPolicy(request),
        note = request.note,
        occurredAt = occurredAt
      )
      savedClub <- IO.blocking {
        {
          updateRecruitmentPolicy(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(updateRecruitmentPolicyAudit(command)).plan(context)
    yield ClubView.fromDomain(savedClub)

  private def updateRecruitmentPolicy(
      connection: java.sql.Connection,
      command: UpdateClubRecruitmentPolicyCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubCapability(        actor = command.actor,
        club = club,
        permission = Permission.ManageClubMembership,
        delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
      )
      commitRecruitmentPolicyUpdate(connection, club, command)
    }

  private def commitRecruitmentPolicyUpdate(
      connection: java.sql.Connection,
      club: Club,
      command: UpdateClubRecruitmentPolicyCommand
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.updateRecruitmentPolicy(club, command.policy))

  private def updateRecruitmentPolicyAudit(command: UpdateClubRecruitmentPolicyCommand): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "club",
        aggregateId = command.clubId.value,
        eventType = "ClubRecruitmentPolicyUpdated",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = Map(
          "applicationsOpen" -> command.policy.applicationsOpen.toString,
          "requirementsText" -> command.policy.requirementsText.getOrElse("none"),
          "expectedReviewSlaHours" -> command.policy.expectedReviewSlaHours.map(_.toString).getOrElse("none")
        ),
        note = command.note
      )
    )

  private final case class UpdateClubRecruitmentPolicyCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      policy: ClubRecruitmentPolicy,
      note: Option[String],
      occurredAt: Instant
  )

  private def recruitmentPolicy(request: UpdateClubRecruitmentPolicyRequest): ClubRecruitmentPolicy =
    request.expectedReviewSlaHours.foreach(hours =>
      require(hours > 0, "expectedReviewSlaHours must be positive")
    )
    ClubRecruitmentPolicy(
      applicationsOpen = request.applicationsOpen,
      requirementsText = request.requirementsText.map(_.trim).filter(_.nonEmpty),
      expectedReviewSlaHours = request.expectedReviewSlaHours
    )
