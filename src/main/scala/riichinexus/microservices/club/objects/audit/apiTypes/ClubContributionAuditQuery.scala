package riichinexus.microservices.club.objects.audit.apiTypes

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 查询俱乐部贡献审计列表时使用的操作者和分页参数。
  *
  * `operatorId` 表示发起查看的人，后端会结合其俱乐部权限决定可返回哪些贡献变更记录。
  */
final case class ClubContributionAuditQuery(
    operatorId: PlayerId,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubContributionAuditQuery:
  given ReadWriter[ClubContributionAuditQuery] = macroRW
