package riichinexus.microservices.platformadmin.router
import riichinexus.system.api.RegisteredAPIMessage


import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.platformadmin.api.{PlatformAdminBanPlayerAPIMessage, PlatformAdminDissolveClubAPIMessage, PlatformAdminGrantSuperAdminAPIMessage}
import riichinexus.microservices.platformadmin.objects.{PlatformAdminClubView, PlatformAdminPlayerView}

object PlatformAdminAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[PlatformAdminBanPlayerAPIMessage, PlatformAdminPlayerView],
      RegisteredAPIMessage.api[PlatformAdminDissolveClubAPIMessage, PlatformAdminClubView],
      RegisteredAPIMessage.api[PlatformAdminGrantSuperAdminAPIMessage, PlatformAdminPlayerView]
    )
