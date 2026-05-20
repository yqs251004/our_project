package riichinexus.microservices.platformadmin.api

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminResponses.given
import upickle.default.*

final case class PlatformAdminGrantSuperAdminAPIMessage(
    playerId: PlayerId,
    operatorId: PlayerId
) extends APIMessage[PlatformAdminPlayerResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlatformAdminPlayerResponse] =
    IO {
      val module = context.support.platformAdminModule
      val request = GrantSuperAdminRequest(operatorId = operatorId)
      val actor = context.support.principal(request.operatorId)
      val grantedAt = Instant.now()

      module.transactionManager.inTransaction {
        if !actor.isSuperAdmin then
          throw AuthorizationFailure("Only an existing super admin can grant super admin access")

        module.tables.findPlayer(playerId).map { player =>
          val updatedPlayer =
            module.playerRepository.save(player.grantRole(RoleGrant.superAdmin(grantedAt, actor.playerId)))
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "player",
              aggregateId = playerId.value,
              eventType = "SuperAdminGranted",
              occurredAt = grantedAt,
              actorId = actor.playerId,
              details = Map("playerId" -> playerId.value),
              note = Some(s"Granted super admin access to ${playerId.value}")
            )
          )
          updatedPlayer
        }
      }
        .map(PlatformAdminPlayerView.fromDomain)
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    }
