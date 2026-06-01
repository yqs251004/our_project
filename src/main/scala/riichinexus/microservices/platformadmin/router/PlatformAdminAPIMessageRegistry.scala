package riichinexus.microservices.platformadmin.router
import riichinexus.api.functions.RegisteredAPIMessageFunctions

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.api.*
import riichinexus.microservices.platformadmin.objects.apiTypes.{PlatformAdminClubView, PlatformAdminPlayerView}
import riichinexus.microservices.platformadmin.objects.apiTypes.*

object PlatformAdminAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessageFunctions.api[PlatformAdminBanPlayerAPIMessage, PlatformAdminPlayerView],
      RegisteredAPIMessageFunctions.api[PlatformAdminDissolveClubAPIMessage, PlatformAdminClubView],
      RegisteredAPIMessageFunctions.api[PlatformAdminGrantSuperAdminAPIMessage, PlatformAdminPlayerView]
    )
