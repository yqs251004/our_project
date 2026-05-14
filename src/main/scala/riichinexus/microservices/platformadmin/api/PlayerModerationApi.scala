package riichinexus.microservices.platformadmin.api

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.platformadmin.api.requests.BanPlayerRequest
import riichinexus.microservices.platformadmin.tables.PlatformAdminTables

object PlayerModerationApi:

  def banPlayer(
      tables: PlatformAdminTables,
      service: SuperAdminService,
      playerId: PlayerId,
      actor: AccessPrincipal,
      request: BanPlayerRequest,
      at: Instant = Instant.now()
  ): Option[Player] =
    tables.findPlayer(playerId).flatMap { _ =>
      service.banPlayer(
        playerId = playerId,
        reason = request.reason,
        actor = actor,
        at = at
      )
    }
