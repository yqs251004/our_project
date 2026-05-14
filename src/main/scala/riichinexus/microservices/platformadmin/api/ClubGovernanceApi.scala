package riichinexus.microservices.platformadmin.api

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.platformadmin.api.requests.DissolveClubRequest
import riichinexus.microservices.platformadmin.tables.PlatformAdminTables

object ClubGovernanceApi:

  def dissolveClub(
      tables: PlatformAdminTables,
      service: SuperAdminService,
      clubId: ClubId,
      actor: AccessPrincipal,
      request: DissolveClubRequest,
      at: Instant = Instant.now()
  ): Option[Club] =
    tables.findClub(clubId).flatMap { _ =>
      service.dissolveClub(
        clubId = clubId,
        actor = actor,
        at = at
      )
    }
