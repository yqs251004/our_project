package riichinexus.system.realtime.domain

import riichinexus.microservices.audit.domain.model.AuditEvent
import riichinexus.system.objects.`private`.AggregateType
import riichinexus.system.realtime.objects.{RealtimeEvent, RealtimeEventType, RealtimeSourceEventType}

object AuditRealtimeMapper:

  def fromAudit(event: AuditEvent): RealtimeEvent =
    RealtimeEvent(
      id = event.id.value,
      eventType = realtimeEventType(event),
      aggregateType = AggregateType.toString(event.aggregateType),
      aggregateId = event.aggregateId,
      occurredAt = event.occurredAt,
      sourceEventType = RealtimeSourceEventType.fromString(event.eventType.toString),
      actorId = event.actorId.map(_.value)
    )

  private def realtimeEventType(event: AuditEvent): RealtimeEventType =
    val normalizedEventType = event.eventType.toString.toLowerCase

    if event.aggregateType == AggregateType.ClubApplication || normalizedEventType.contains("application") then
      RealtimeEventType.ClubApplicationChanged
    else if normalizedEventType.contains("member") then
      RealtimeEventType.ClubMemberChanged
    else if event.aggregateType == AggregateType.Club then
      RealtimeEventType.ClubChanged
    else if event.aggregateType == AggregateType.Appeal || event.aggregateType == AggregateType.AppealTicket then
      RealtimeEventType.AppealChanged
    else if event.aggregateType == AggregateType.TournamentTable || event.aggregateType == AggregateType.MahjongTable then
      RealtimeEventType.TournamentTableChanged
    else if event.aggregateType == AggregateType.Tournament || event.aggregateType == AggregateType.TournamentSettlement then
      RealtimeEventType.TournamentChanged
    else if event.aggregateType == AggregateType.Player then
      RealtimeEventType.PlayerChanged
    else
      RealtimeEventType.DomainChanged
