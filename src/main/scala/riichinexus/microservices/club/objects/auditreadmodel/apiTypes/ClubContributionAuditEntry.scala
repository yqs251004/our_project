package riichinexus.microservices.club.objects.auditreadmodel.apiTypes

import upickle.default.ReadWriter
import riichinexus.system.json.JsonCodecs.given

/** ClubContributionAuditEntry 表示前后端共享的俱乐部贡献审计条目 数据结构，包含 ID、俱乐部 ID、玩家 ID、delta、contribution、occurredAt等。 */

final case class ClubContributionAuditEntry(
    id: String,
    clubId: String,
    playerId: Option[String],
    delta: Option[String],
    contribution: Option[String],
    occurredAt: String,
    actorId: Option[String],
    note: Option[String]
) derives ReadWriter
