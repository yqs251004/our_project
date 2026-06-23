package riichinexus.system.json

import riichinexus.microservices.audit.domain.model.AuditEvent
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.audit.objects.`private`.AuditEventPrivateView
import riichinexus.system.objects.`private`.AggregateType
import riichinexus.system.json.JsonCodecSupport.stringEnumReadWriter
import riichinexus.system.json.SharedJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

object AuditJsonCodecs:
  given ReadWriter[AggregateType] =
    stringEnumReadWriter(AggregateType.fromString, AggregateType.toString)
  given ReadWriter[AuditEventType] =
    stringEnumReadWriter(AuditEventType.fromString, AuditEventType.toString)
  given ReadWriter[AuditEvent] = macroRW
  given ReadWriter[AuditEventDraft] = macroRW
  given ReadWriter[AuditEventPrivateView] = macroRW
