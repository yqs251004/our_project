package riichinexus.microservices.platformadmin.api

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.bootstrap.PlatformAdminModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.ClubDissolved
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminClubView
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import upickle.default.*

final case class PlatformAdminDissolveClubAPIMessage(
    clubId: ClubId,
    operatorId: PlayerId
) extends APIMessage[PlatformAdminClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlatformAdminClubView] =
    for
      actor <- IO.blocking(context.principal(operatorId))
      module = context.support.platformAdminModule
      request = DissolveClubRequest(operatorId = operatorId)
      dissolvedAt <- IO.realTimeInstant
      command = DissolveClubCommand(
        clubId = clubId,
        actor = actor,
        dissolvedAt = dissolvedAt
      )
      club <- IO.blocking {
        module.transactionManager
          .inTransaction {
            dissolveClub(context.connection, module, command)
          }
          .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
      }
    yield PlatformAdminClubView.fromDomain(club)

  private def dissolveClub(
      connection: java.sql.Connection,
      module: PlatformAdminModuleContext,
      command: DissolveClubCommand
  ): Option[Club] =
    module.authorizationService.requirePermission(command.actor, Permission.DissolveClub)
    ClubTable.findById(connection, command.clubId).map { club =>
      ensureClubCanDissolve(club, command.clubId)
      removeMembersFromClub(connection, club, command.clubId)
      removeRelationsToClub(connection, command.clubId)
      commitDissolvedClub(connection, module, club, command)
    }

  private def ensureClubCanDissolve(club: Club, clubId: ClubId): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${clubId.value} has already been dissolved")

  private def removeMembersFromClub(connection: java.sql.Connection, club: Club, clubId: ClubId): Unit =
    club.members.foreach { memberId =>
      PlayerTable.findById(connection, memberId).foreach { player =>
        PlayerTable.save(
          connection,
          player
            .leaveClub(clubId)
            .revokeClubAdmin(clubId)
        )
      }
    }

  private def removeRelationsToClub(connection: java.sql.Connection, clubId: ClubId): Unit =
    riichinexus.microservices.club.tables.club.ClubTable.findFiltered(connection, activeOnly = true)
      .filterNot(_.id == clubId)
      .filter(_.relations.exists(_.targetClubId == clubId))
      .foreach { relatedClub =>
        riichinexus.microservices.club.tables.club.ClubTable.save(connection, relatedClub.removeRelation(clubId))
      }

  private def commitDissolvedClub(
      connection: java.sql.Connection,
      module: PlatformAdminModuleContext,
      club: Club,
      command: DissolveClubCommand
  ): Club =
    DomainChangeInterpreter
      .auditAndEvents(module.transactionManager, module.auditEventRepository, module.eventBus)
      .commitWithinTransaction(
        DomainChange(
          aggregate = club.dissolve(command.actor.playerId.getOrElse(club.creator), command.dissolvedAt),
          persist = updatedClub => ClubTable.save(connection, updatedClub),
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
