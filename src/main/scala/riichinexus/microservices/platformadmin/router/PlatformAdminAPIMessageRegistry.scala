package riichinexus.microservices.platformadmin.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.api.*
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminResponses.given

object PlatformAdminAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[PlatformAdminBanPlayerAPIMessage, PlatformAdminPlayerResponse],
      RegisteredAPIMessage.api[PlatformAdminDissolveClubAPIMessage, PlatformAdminClubResponse],
      RegisteredAPIMessage.api[PlatformAdminGrantSuperAdminAPIMessage, PlatformAdminPlayerResponse]
    )
