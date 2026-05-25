package riichinexus.microservices.platformadmin.api

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.bootstrap.PlatformAdminModuleContext
import riichinexus.domain.event.PlayerBanned
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminResponses.given
import upickle.default.*

final case class PlatformAdminBanPlayerAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId,
    reason: String
) extends APIMessage[PlatformAdminPlayerResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlatformAdminPlayerResponse] =
    for
      actor <- IO(context.support.principal(operatorId))
      module = context.support.platformAdminModule
      request = BanPlayerRequest(operatorId = operatorId, reason = reason)
      bannedAt <- IO.realTimeInstant
      command = BanPlayerCommand(
        playerId = playerId,
        actor = actor,
        reason = request.reason,
        bannedAt = bannedAt
      )
      player <- IO {
        module.transactionManager
          .inTransaction {
            banPlayer(module, command)
          }
          .getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found"))
      }
    yield PlatformAdminPlayerView.fromDomain(player)

  private def banPlayer(
      module: PlatformAdminModuleContext,
      command: BanPlayerCommand
  ): Option[Player] =
    module.authorizationService.requirePermission(command.actor, Permission.BanRegisteredPlayer)
    require(command.reason.trim.nonEmpty, "Ban reason cannot be empty")

    module.tables.findPlayer(command.playerId).map { player =>
      DomainChangeInterpreter
        .auditAndEvents(module.transactionManager, module.auditEventRepository, module.eventBus)
        .commitWithinTransaction(
          DomainChange(
            aggregate = player.ban(command.reason),
            persist = module.playerRepository.save,
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
