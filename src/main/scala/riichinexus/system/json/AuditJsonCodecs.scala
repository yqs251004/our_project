package riichinexus.system.json

import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.{AuditEventDraft, AuditEventPrivateView}
import riichinexus.system.json.JsonCodecSupport.stringEnumReadWriter
import riichinexus.system.json.SharedJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

object AuditJsonCodecs:
  given ReadWriter[AuditEventType] =
    stringEnumReadWriter(AuditEventType.fromString, AuditEventType.toString)
  given ReadWriter[AuditEvent] = macroRW
  given ReadWriter[AuditEventDraft] = macroRW
  given ReadWriter[AuditEventPrivateView] = macroRW
