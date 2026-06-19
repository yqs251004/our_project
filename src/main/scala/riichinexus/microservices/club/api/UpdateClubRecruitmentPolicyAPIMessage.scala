package riichinexus.microservices.club.api
import riichinexus.microservices.club.domain.functions.ClubViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubRecruitmentPolicy

import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.UpdateClubRecruitmentPolicyRequest
/** 更新俱乐部招募策略。 */
final case class UpdateClubRecruitmentPolicyAPIMessage(
    clubId: String,
    request: UpdateClubRecruitmentPolicyRequest
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
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
    yield ClubViewFunctions.clubView(savedClub)

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

  private def updateRecruitmentPolicyAudit(command: UpdateClubRecruitmentPolicyCommand): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "club",
        aggregateId = command.clubId.value,
        eventType = AuditEventType.ClubRecruitmentPolicyUpdated,
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
      actor: AccessPrincipalPrivateView,
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
