package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 平台管理员封禁玩家时提交的请求体。
  *
  * `operatorId` 是执行封禁的管理员，`reason` 会写入玩家状态和审计记录，供后续平台排查与展示。
  */
final case class BanPlayerRequest(
    operatorId: PlayerId,
    reason: String
) derives ReadWriter
