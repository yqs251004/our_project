package riichinexus.system.json

import riichinexus.microservices.audit.objects.`private`.{AuditEventDraft, AuditEventPrivateView}
import riichinexus.system.json.SharedJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

object AuditJsonCodecs:
  given ReadWriter[AuditEventDraft] = macroRW
  given ReadWriter[AuditEventPrivateView] = macroRW
