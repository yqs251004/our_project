package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** BanPlayerRequest 表示Ban玩家请求 的前端请求参数，包含operatorId、reason。 */

final case class BanPlayerRequest(
    operatorId: PlayerId,
    reason: String
) derives ReadWriter
