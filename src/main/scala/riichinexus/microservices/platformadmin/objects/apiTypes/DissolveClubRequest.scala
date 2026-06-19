package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** DissolveClubRequest 表示Dissolve俱乐部请求 的前端请求参数，包含operatorId。 */

final case class DissolveClubRequest(
    operatorId: PlayerId
) derives ReadWriter
