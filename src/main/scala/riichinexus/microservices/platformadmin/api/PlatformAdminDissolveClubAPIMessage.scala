package riichinexus.microservices.platformadmin.api

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.bootstrap.PlatformAdminModuleContext
import riichinexus.domain.event.ClubDissolved
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminResponses.given
import upickle.default.*

final case class PlatformAdminDissolveClubAPIMessage(
    clubId: ClubId,
    operatorId: PlayerId
) extends APIMessage[PlatformAdminClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlatformAdminClubResponse] =
    for
      actor <- IO(context.support.principal(operatorId))
      module = context.support.platformAdminModule
      request = DissolveClubRequest(operatorId = operatorId)
      dissolvedAt <- IO.realTimeInstant
      command = DissolveClubCommand(
        clubId = clubId,
        actor = actor,
        dissolvedAt = dissolvedAt
      )
      club <- IO {
        module.transactionManager
          .inTransaction {
            dissolveClub(module, command)
          }
          .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
      }
    yield PlatformAdminClubView.fromDomain(club)

  private def dissolveClub(
      module: PlatformAdminModuleContext,
      command: DissolveClubCommand
  ): Option[Club] =
    module.authorizationService.requirePermission(command.actor, Permission.DissolveClub)
    module.tables.findClub(command.clubId).map { club =>
      ensureClubCanDissolve(club, command.clubId)
      removeMembersFromClub(module, club, command.clubId)
      removeRelationsToClub(module, command.clubId)
      commitDissolvedClub(module, club, command)
    }

  private def ensureClubCanDissolve(club: Club, clubId: ClubId): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${clubId.value} has already been dissolved")

  private def removeMembersFromClub(module: PlatformAdminModuleContext, club: Club, clubId: ClubId): Unit =
    club.members.foreach { memberId =>
      module.playerRepository.findById(memberId).foreach { player =>
        module.playerRepository.save(
          player
            .leaveClub(clubId)
            .revokeClubAdmin(clubId)
        )
      }
    }

  private def removeRelationsToClub(module: PlatformAdminModuleContext, clubId: ClubId): Unit =
    module.clubRepository.findActive()
      .filterNot(_.id == clubId)
      .filter(_.relations.exists(_.targetClubId == clubId))
      .foreach { relatedClub =>
        module.clubRepository.save(relatedClub.removeRelation(clubId))
      }

  private def commitDissolvedClub(
      module: PlatformAdminModuleContext,
      club: Club,
      command: DissolveClubCommand
  ): Club =
    DomainChangeInterpreter
      .auditAndEvents(module.transactionManager, module.auditEventRepository, module.eventBus)
      .commitWithinTransaction(
        DomainChange(
          aggregate = club.dissolve(command.actor.playerId.getOrElse(club.creator), command.dissolvedAt),
          persist = module.clubRepository.save,
          auditEntries = _ =>
            Vector(
              AuditEventEntry(
                id = IdGenerator.auditEventId(),
                aggregateType = "club",
                aggregateId = command.clubId.value,
                eventType = "ClubDissolved",
                occurredAt = command.dissolvedAt,
                actorId = command.actor.playerId,
                details = Map("memberCount" -> club.members.size.toString),
                note = Some(s"Club ${command.clubId.value} dissolved")
              )
            ),
          domainEvents = _ => Vector(ClubDissolved(command.clubId, command.dissolvedAt))
        )
      )

  private final case class DissolveClubCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      dissolvedAt: Instant
  )
