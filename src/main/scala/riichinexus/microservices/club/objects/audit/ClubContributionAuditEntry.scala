package riichinexus.microservices.club.objects.audit

import upickle.default.{ReadWriter, macroRW}
import riichinexus.system.json.JsonCodecs.given

/** 俱乐部贡献值变更历史中的一条审计读模型。
  *
  * 管理页用它展示谁在何时调整了哪位成员的贡献值、调整量和调整后结果；字段保持字符串化是为了直接承载审计明细。
  */
final case class ClubContributionAuditEntry(
    id: String,
    clubId: String,
    playerId: Option[String],
    delta: Option[String],
    contribution: Option[String],
    occurredAt: String,
    actorId: Option[String],
    note: Option[String]
)

object ClubContributionAuditEntry:
  given ReadWriter[ClubContributionAuditEntry] = macroRW
