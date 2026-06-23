package riichinexus.microservices.club.api.profile

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.membership.model.ClubRecruitmentPolicy

import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode
import riichinexus.microservices.club.objects.profile.ClubView
import riichinexus.microservices.club.objects.membership.apiTypes.UpdateClubRecruitmentPolicyRequest
/** 更新俱乐部招募策略。 */
final case class UpdateClubRecruitmentPolicyAPIMessage(
    clubId: String,
    request: UpdateClubRecruitmentPolicyRequest
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      occurredAt <- IO.realTimeInstant
      requestedClubId = ClubId(clubId)
      policy = recruitmentPolicy(request)
      savedClub <- IO.blocking {
        updateRecruitmentPolicy(context.connection, requestedClubId, actor, policy)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
      _ <- RecordAuditEventsPrivateAPIMessage(updateRecruitmentPolicyAudit(requestedClubId, actor, policy, request.note, occurredAt)).plan(context)
    yield ClubViewFunctions.clubView(savedClub)

  private def updateRecruitmentPolicy(
      connection: java.sql.Connection,
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      policy: ClubRecruitmentPolicy
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubCapability(
        actor = actor,
        club = club,
        permission = Permission.ManageClubMembership,
        delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
      )
      commitRecruitmentPolicyUpdate(connection, club, policy)
    }

  private def commitRecruitmentPolicyUpdate(
      connection: java.sql.Connection,
      club: Club,
      policy: ClubRecruitmentPolicy
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.updateRecruitmentPolicy(club, policy))

  private def updateRecruitmentPolicyAudit(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      policy: ClubRecruitmentPolicy,
      note: Option[String],
      occurredAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Club,
        aggregateId = clubId.value,
        eventType = AuditEventType.ClubRecruitmentPolicyUpdated,
        occurredAt = occurredAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.ApplicationsOpen) -> policy.applicationsOpen.toString,
          StructuredEventField.toString(StructuredEventField.RequirementsText) -> policy.requirementsText.getOrElse("none"),
          StructuredEventField.toString(StructuredEventField.ExpectedReviewSlaHours) -> policy.expectedReviewSlaHours.map(_.toString).getOrElse("none")
        ),
        note = note
      )
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

