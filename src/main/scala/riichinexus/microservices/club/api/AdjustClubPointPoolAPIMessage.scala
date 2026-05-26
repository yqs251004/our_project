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
import upickle.default.*

final case class AdjustClubPointPoolAPIMessage(
    clubId: String,
    operatorId: String,
    delta: Int,
    note: Option[String] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(context.principal(PlayerId(operatorId)))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AdjustClubPointPoolCommand(
        clubId = ClubId(clubId),
        actor = actor,
        delta = delta,
        note = note,
        occurredAt = occurredAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          adjustPointPool(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def adjustPointPool(
      module: ClubModuleContext,
      command: AdjustClubPointPoolCommand
  ): Option[Club] =
    module.clubRepository.findById(command.clubId).map { club =>
      ensureClubActive(club)
      requireClubCapability(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations,
        delegatedPrivileges = Set(ClubPrivilege.ManageBank)
      )
      commitPointPoolAdjustment(module, club, command)
    }

  private def commitPointPoolAdjustment(
      module: ClubModuleContext,
      club: Club,
      command: AdjustClubPointPoolCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.adjustPointPool(command.delta),
        persist = module.clubRepository.save,
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubPointPoolAdjusted",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = updatedClub =>
          Map(
            "delta" -> command.delta.toString,
            "pointPool" -> updatedClub.pointPool.toString
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

  private final case class AdjustClubPointPoolCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      delta: Int,
      note: Option[String],
      occurredAt: Instant
  )
