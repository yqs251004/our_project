package riichinexus.microservices.platformadmin.api

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.bootstrap.PlatformAdminModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.platformadmin.objects.PlatformAdminPlayerView
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import upickle.default.*

final case class PlatformAdminGrantSuperAdminAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId
) extends APIMessage[PlatformAdminPlayerView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlatformAdminPlayerView] =
    for
      actor <- IO(context.principal(operatorId))
      module = context.support.platformAdminModule
      request = GrantSuperAdminRequest(operatorId = operatorId)
      grantedAt <- IO.realTimeInstant
      command = GrantSuperAdminCommand(
        playerId = playerId,
        actor = actor,
        grantedAt = grantedAt
      )
      player <- IO {
        module.transactionManager
          .inTransaction {
            grantSuperAdmin(context.connection, module, command)
          }
          .getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found"))
      }
    yield PlatformAdminPlayerView.fromDomain(player)

  private def grantSuperAdmin(
      connection: java.sql.Connection,
      module: PlatformAdminModuleContext,
    command: GrantSuperAdminCommand
  ): Option[Player] =
    ensureSuperAdmin(command.actor)
    PlayerTable.findById(connection, command.playerId).map { player =>
      DomainChangeInterpreter
        .auditOnly(module.transactionManager, module.auditEventRepository)
        .commitWithinTransaction(
          DomainChange(
            aggregate = player.grantRole(RoleGrant.superAdmin(command.grantedAt, command.actor.playerId)),
            persist = nextPlayer => PlayerTable.save(connection, nextPlayer),
            auditEntries = _ =>
              Vector(
                AuditEventEntry(
                  id = IdGenerator.auditEventId(),
                  aggregateType = "player",
                  aggregateId = command.playerId.value,
                  eventType = "SuperAdminGranted",
                  occurredAt = command.grantedAt,
                  actorId = command.actor.playerId,
                  details = Map("playerId" -> command.playerId.value),
                  note = Some(s"Granted super admin access to ${command.playerId.value}")
                )
              )
          )
        )
    }

  private def ensureSuperAdmin(actor: AccessPrincipal): Unit =
    if !actor.isSuperAdmin then
      throw AuthorizationFailure("Only an existing super admin can grant super admin access")

  private final case class GrantSuperAdminCommand(
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant
  )
