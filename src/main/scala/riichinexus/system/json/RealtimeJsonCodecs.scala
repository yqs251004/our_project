package riichinexus.system.json

import riichinexus.system.json.JsonCodecSupport.stringEnumReadWriter
import riichinexus.system.realtime.objects.RealtimeEventType
import upickle.default.ReadWriter

object RealtimeJsonCodecs:
  given ReadWriter[RealtimeEventType] =
    stringEnumReadWriter(RealtimeEventType.fromString, RealtimeEventType.toString)
