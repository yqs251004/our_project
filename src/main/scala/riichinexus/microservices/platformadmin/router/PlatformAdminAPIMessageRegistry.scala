package riichinexus.microservices.platformadmin.router
import riichinexus.system.api.RegisteredAPIMessage

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.api.*
import riichinexus.microservices.platformadmin.objects.apiTypes.{PlatformAdminClubView, PlatformAdminPlayerView}
import riichinexus.microservices.platformadmin.objects.apiTypes.*

object PlatformAdminAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[PlatformAdminBanPlayerAPIMessage, PlatformAdminPlayerView],
      RegisteredAPIMessage.api[PlatformAdminDissolveClubAPIMessage, PlatformAdminClubView],
      RegisteredAPIMessage.api[PlatformAdminGrantSuperAdminAPIMessage, PlatformAdminPlayerView]
    )
