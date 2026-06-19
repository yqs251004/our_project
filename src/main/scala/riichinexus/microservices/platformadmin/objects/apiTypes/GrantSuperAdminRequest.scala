package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** GrantSuperAdminRequest 表示Grant超级管理员请求 的前端请求参数，包含operatorId。 */

final case class GrantSuperAdminRequest(
    operatorId: PlayerId
) derives ReadWriter
