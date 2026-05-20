package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpdateClubRecruitmentPolicyAPIMessage(
    clubId: String,
    operatorId: String,
    applicationsOpen: Boolean,
    requirementsText: Option[String] = None,
    expectedReviewSlaHours: Option[Int] = None,
    note: Option[String] = None
) extends APIMessage[Club] derives ReadWriter:

  expectedReviewSlaHours.foreach(hours =>
    require(hours > 0, "Recruitment policy expectedReviewSlaHours must be positive")
  )

  override def plan(context: ApiPlanContext): IO[Club] =
    IO {
      val policy = ClubRecruitmentPolicy(
        applicationsOpen = applicationsOpen,
        requirementsText = requirementsText.map(_.trim).filter(_.nonEmpty),
        expectedReviewSlaHours = expectedReviewSlaHours
      )
      val module = context.support.clubModule
      val parsedClubId = ClubId(clubId)
      val actor = context.support.principal(PlayerId(operatorId))
      val occurredAt = java.time.Instant.now()

      module.transactionManager.inTransaction {
        module.clubRepository.findById(parsedClubId).map { club =>
          ensureClubActive(club)
          requireClubCapability(
            context = context,
            actor = actor,
            club = club,
            permission = Permission.ManageClubMembership,
            delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
          )

          val updatedClub = module.clubRepository.save(club.updateRecruitmentPolicy(policy))
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "club",
              aggregateId = parsedClubId.value,
              eventType = "ClubRecruitmentPolicyUpdated",
              occurredAt = occurredAt,
              actorId = actor.playerId,
              details = Map(
                "applicationsOpen" -> policy.applicationsOpen.toString,
                "requirementsText" -> policy.requirementsText.getOrElse("none"),
                "expectedReviewSlaHours" -> policy.expectedReviewSlaHours.map(_.toString).getOrElse("none")
              ),
              note = note
            )
          )
          updatedClub
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubCapability(
      context: ApiPlanContext,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[String]
  ): Unit =
    val authorizationService = context.support.clubModule.authorizationService
    val hasBasePermission = authorizationService.can(actor, permission, clubId = Some(club.id))
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
        delegatedPrivileges.exists(privilege => club.hasPrivilege(playerId, privilege))
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )
