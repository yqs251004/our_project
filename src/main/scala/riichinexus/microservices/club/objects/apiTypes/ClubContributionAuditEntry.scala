package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.{AuditEventEntry as DomainAuditEventEntry, ClubId}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

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

object ClubContributionAuditEntry:
  def fromDomain(clubId: ClubId, entry: DomainAuditEventEntry): ClubContributionAuditEntry =
    ClubContributionAuditEntry(
      id = entry.id.value,
      clubId = clubId.value,
      playerId = entry.details.get("playerId"),
      delta = entry.details.get("delta"),
      contribution = entry.details.get("contribution"),
      occurredAt = entry.occurredAt.toString,
      actorId = entry.actorId.map(_.value),
      note = entry.note
    )

