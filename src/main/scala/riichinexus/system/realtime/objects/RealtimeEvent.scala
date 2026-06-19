package riichinexus.system.realtime.objects

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, Writer, writeJs}

final case class RealtimeEvent(
    id: String,
    eventType: RealtimeEventType,
    aggregateType: String,
    aggregateId: String,
    occurredAt: Instant,
    sourceEventType: String,
    actorId: Option[String] = None,
    recipientPlayerId: Option[String] = None,
    title: Option[String] = None,
    body: Option[String] = None,
    severity: Option[String] = None,
    actionUrl: Option[String] = None,
    data: Option[ujson.Value] = None
) derives ReadWriter

object RealtimeEvent:
  def data[A: Writer](value: A): ujson.Value =
    writeJs(value)
