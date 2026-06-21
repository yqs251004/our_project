package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 平台管理员解散俱乐部时提交的请求体。
  *
  * 请求只携带操作者，目标俱乐部来自路径；后端会记录解散时间和解散人，避免俱乐部被物理删除。
  */
final case class DissolveClubRequest(
    operatorId: PlayerId
) derives ReadWriter
