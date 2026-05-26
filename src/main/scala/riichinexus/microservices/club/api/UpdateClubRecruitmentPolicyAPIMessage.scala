package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.{Club as ClubResponse}
import riichinexus.microservices.club.objects.apiTypes.UpdateClubRecruitmentPolicyRequest
import upickle.default.*

final case class UpdateClubRecruitmentPolicyAPIMessage(
    clubId: String,
    request: UpdateClubRecruitmentPolicyRequest
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(context.principal(request.operator))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = UpdateClubRecruitmentPolicyCommand(
        clubId = ClubId(clubId),
        actor = actor,
        policy = request.policy,
        note = request.note,
        occurredAt = occurredAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          updateRecruitmentPolicy(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def updateRecruitmentPolicy(
      module: ClubModuleContext,
      command: UpdateClubRecruitmentPolicyCommand
  ): Option[Club] =
    module.clubRepository.findById(command.clubId).map { club =>
      ensureClubActive(club)
      requireClubCapability(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubMembership,
        delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
      )
      commitRecruitmentPolicyUpdate(module, club, command)
    }

  private def commitRecruitmentPolicyUpdate(
      module: ClubModuleContext,
      club: Club,
      command: UpdateClubRecruitmentPolicyCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.updateRecruitmentPolicy(command.policy),
        persist = module.clubRepository.save,
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubRecruitmentPolicyUpdated",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = _ =>
          Map(
            "applicationsOpen" -> command.policy.applicationsOpen.toString,
            "requirementsText" -> command.policy.requirementsText.getOrElse("none"),
            "expectedReviewSlaHours" -> command.policy.expectedReviewSlaHours.map(_.toString).getOrElse("none")
          ),
        note = command.note
      )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubCapability(
      module: ClubModuleContext,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[String]
  ): Unit =
    val authorizationService = module.authorizationService
    val hasBasePermission = authorizationService.can(actor, permission, clubId = Some(club.id))
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
        delegatedPrivileges.exists(privilege => club.hasPrivilege(playerId, privilege))
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )

  private final case class UpdateClubRecruitmentPolicyCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      policy: ClubRecruitmentPolicy,
      note: Option[String],
      occurredAt: Instant
  )
