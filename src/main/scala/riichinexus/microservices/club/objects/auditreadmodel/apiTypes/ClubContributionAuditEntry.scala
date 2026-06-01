package riichinexus.microservices.club.objects.auditreadmodel.apiTypes

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
