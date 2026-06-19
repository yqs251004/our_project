package riichinexus.system.realtime.domain

import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.system.realtime.objects.{RealtimeEvent, RealtimeEventType}

object AuditRealtimeMapper:

  def fromAudit(event: AuditEvent): RealtimeEvent =
    RealtimeEvent(
      id = event.id.value,
      eventType = realtimeEventType(event),
      aggregateType = event.aggregateType,
      aggregateId = event.aggregateId,
      occurredAt = event.occurredAt,
      sourceEventType = event.eventType.toString,
      actorId = event.actorId.map(_.value)
    )

  private def realtimeEventType(event: AuditEvent): RealtimeEventType =
    val normalizedAggregateType = event.aggregateType.trim.toLowerCase
    val normalizedEventType = event.eventType.toString.toLowerCase

    if normalizedAggregateType.contains("application") || normalizedEventType.contains("application") then
      RealtimeEventType.ClubApplicationChanged
    else if normalizedAggregateType.contains("member") || normalizedEventType.contains("member") then
      RealtimeEventType.ClubMemberChanged
    else if normalizedAggregateType.contains("club") then
      RealtimeEventType.ClubChanged
    else if normalizedAggregateType.contains("appeal") then
      RealtimeEventType.AppealChanged
    else if normalizedAggregateType.contains("table") then
      RealtimeEventType.TournamentTableChanged
    else if normalizedAggregateType.contains("tournament") then
      RealtimeEventType.TournamentChanged
    else if normalizedAggregateType.contains("player") then
      RealtimeEventType.PlayerChanged
    else
      RealtimeEventType.DomainChanged
