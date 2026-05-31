package riichinexus.microservices.platformadmin.api

import cats.effect.unsafe.implicits.global
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
import riichinexus.microservices.club.api.`private`.{ListClubsPrivateAPIMessage, ResolveClubPrivateAPIMessage, ResolveClubsPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
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
    yield platformAdminClubView(club)

  private def dissolveClub(
      connection: java.sql.Connection,
      module: PlatformAdminModuleContext,
      command: DissolveClubCommand
  ): Option[Club] =
    module.authorizationService.requirePermission(command.actor, Permission.DissolveClub)
    ResolveClubPrivateAPIMessage(command.clubId).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync().map { club =>
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
      GetPlayerAPIMessage.findPlayer(connection, memberId).foreach { player =>
        CreatePlayerAPIMessage.persistPlayer(
          connection,
          player
            .leaveClub(clubId)
            .revokeClubAdmin(clubId)
        )
      }
    }

  private def removeRelationsToClub(connection: java.sql.Connection, clubId: ClubId): Unit =
    ListClubsPrivateAPIMessage(activeOnly = true).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync()
      .filterNot(_.id == clubId)
      .filter(_.relations.exists(_.targetClubId == clubId))
      .foreach { relatedClub =>
        SaveClubPrivateAPIMessage(relatedClub.removeRelation(clubId))
          .plan(ApiPlanContext(support = null, bearerToken = None, connection = connection))
          .unsafeRunSync()
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
          persist = updatedClub => SaveClubPrivateAPIMessage(updatedClub).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync(),
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

  private def platformAdminClubView(club: Club): PlatformAdminClubView =
    PlatformAdminClubView(
      clubId = club.id.value,
      name = club.name,
      creator = club.creator.value,
      createdAt = club.createdAt.toString,
      memberCount = club.members.size,
      adminCount = club.admins.size,
      totalPoints = club.totalPoints,
      powerRating = club.powerRating,
      dissolvedAt = club.dissolvedAt.map(_.toString),
      dissolvedBy = club.dissolvedBy.map(_.value)
    )

  private final case class DissolveClubCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      dissolvedAt: Instant
  )
