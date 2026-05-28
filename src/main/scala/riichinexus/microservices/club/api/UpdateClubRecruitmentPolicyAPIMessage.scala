package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.auth.domain.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.ClubView
import riichinexus.microservices.club.objects.apiTypes.UpdateClubRecruitmentPolicyRequest
import upickle.default.*

final case class UpdateClubRecruitmentPolicyAPIMessage(
    clubId: String,
    request: UpdateClubRecruitmentPolicyRequest
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(context.principal(request.operator))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = UpdateClubRecruitmentPolicyCommand(
        clubId = ClubId(clubId),
        actor = actor,
        policy = request.policy,
        note = request.note,
        occurredAt = occurredAt
      )
      club <- IO.blocking {
        module.transactionManager.inTransaction {
          updateRecruitmentPolicy(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def updateRecruitmentPolicy(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: UpdateClubRecruitmentPolicyCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.club.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubCapability(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubMembership,
        delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
      )
      commitRecruitmentPolicyUpdate(connection, module, club, command)
    }

  private def commitRecruitmentPolicyUpdate(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      club: Club,
      command: UpdateClubRecruitmentPolicyCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.updateRecruitmentPolicy(command.policy),
        persist = updatedClub => riichinexus.microservices.club.tables.club.ClubTable.save(connection, updatedClub),
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

  private final case class UpdateClubRecruitmentPolicyCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      policy: ClubRecruitmentPolicy,
      note: Option[String],
      occurredAt: Instant
  )
