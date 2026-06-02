package riichinexus.system.realtime.domain

import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.system.realtime.objects.RealtimeEvent

object AuditRealtimeMapper:

  def fromAudit(event: AuditEvent): RealtimeEvent =
    RealtimeEvent(
      id = event.id.value,
      eventType = realtimeEventType(event),
      aggregateType = event.aggregateType,
      aggregateId = event.aggregateId,
      occurredAt = event.occurredAt,
      sourceEventType = event.eventType,
      actorId = event.actorId.map(_.value)
    )

  private def realtimeEventType(event: AuditEvent): String =
    val normalizedAggregateType = event.aggregateType.trim.toLowerCase
    val normalizedEventType = event.eventType.trim.toLowerCase

    if normalizedAggregateType.contains("application") || normalizedEventType.contains("application") then
      "ClubApplicationChanged"
    else if normalizedAggregateType.contains("member") || normalizedEventType.contains("member") then
      "ClubMemberChanged"
    else if normalizedAggregateType.contains("club") then
      "ClubChanged"
    else if normalizedAggregateType.contains("appeal") then
      "AppealChanged"
    else if normalizedAggregateType.contains("table") then
      "TournamentTableChanged"
    else if normalizedAggregateType.contains("tournament") then
      "TournamentChanged"
    else if normalizedAggregateType.contains("player") then
      "PlayerChanged"
    else
      "DomainChanged"
