package riichinexus.microservices.platformadmin.api

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
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
    IO {
      val module = context.support.platformAdminModule
      val request = BanPlayerRequest(operatorId = operatorId, reason = reason)
      val actor = context.support.principal(request.operatorId)
      val bannedAt = Instant.now()

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.BanRegisteredPlayer)
        require(request.reason.trim.nonEmpty, "Ban reason cannot be empty")

        module.tables.findPlayer(playerId).map { player =>
          val banned = module.playerRepository.save(player.ban(request.reason))
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "player",
              aggregateId = playerId.value,
              eventType = "PlayerBanned",
              occurredAt = bannedAt,
              actorId = actor.playerId,
              details = Map("reason" -> request.reason),
              note = Some(request.reason)
            )
          )
          module.eventBus.publish(PlayerBanned(playerId, request.reason, bannedAt))
          banned
        }
      }
        .map(PlatformAdminPlayerView.fromDomain)
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    }
