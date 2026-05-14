package riichinexus.microservices.platformadmin.api

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.platformadmin.api.requests.GrantSuperAdminRequest
import riichinexus.microservices.platformadmin.tables.PlatformAdminTables

object PlatformRoleApi:

  def grantSuperAdmin(
      tables: PlatformAdminTables,
      service: SuperAdminService,
      playerId: PlayerId,
      actor: AccessPrincipal,
      request: GrantSuperAdminRequest,
      grantedAt: Instant = Instant.now()
  ): Option[Player] =
    tables.findPlayer(playerId).flatMap { _ =>
      service.grantSuperAdmin(
        playerId = playerId,
        actor = actor,
        grantedAt = grantedAt
      )
    }
