package riichinexus.microservices.platformadmin.api

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.bootstrap.PlatformAdminModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.player.domain.PlayerBanned
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminPlayerView
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import upickle.default.*

final case class PlatformAdminBanPlayerAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId,
    reason: String
) extends APIMessage[PlatformAdminPlayerView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlatformAdminPlayerView] =
    for
      actor <- IO.blocking(context.principal(operatorId))
      module = context.support.platformAdminModule
      request = BanPlayerRequest(operatorId = operatorId, reason = reason)
      bannedAt <- IO.realTimeInstant
      command = BanPlayerCommand(
        playerId = playerId,
        actor = actor,
        reason = request.reason,
        bannedAt = bannedAt
      )
      player <- IO.blocking {
        module.transactionManager
          .inTransaction {
            banPlayer(context.connection, module, command)
          }
          .getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found"))
      }
    yield PlatformAdminPlayerView.fromDomain(player)

  private def banPlayer(
      connection: java.sql.Connection,
      module: PlatformAdminModuleContext,
      command: BanPlayerCommand
  ): Option[Player] =
    module.authorizationService.requirePermission(command.actor, Permission.BanRegisteredPlayer)
    require(command.reason.trim.nonEmpty, "Ban reason cannot be empty")

    PlayerTable.findById(connection, command.playerId).map { player =>
      DomainChangeInterpreter
        .auditAndEvents(module.transactionManager, module.auditEventRepository, module.eventBus)
        .commitWithinTransaction(
          DomainChange(
            aggregate = player.ban(command.reason),
            persist = nextPlayer => PlayerTable.save(connection, nextPlayer),
            auditEntries = _ =>
              Vector(
                AuditEventEntry(
                  id = IdGenerator.auditEventId(),
                  aggregateType = "player",
                  aggregateId = command.playerId.value,
                  eventType = "PlayerBanned",
                  occurredAt = command.bannedAt,
                  actorId = command.actor.playerId,
                  details = Map("reason" -> command.reason),
                  note = Some(command.reason)
                )
              ),
            domainEvents = _ => Vector(PlayerBanned(command.playerId, command.reason, command.bannedAt))
          )
        )
    }

  private final case class BanPlayerCommand(
      playerId: PlayerId,
      actor: AccessPrincipal,
      reason: String,
      bannedAt: Instant
  )
