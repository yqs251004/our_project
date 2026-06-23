package riichinexus.system.realtime.objects

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import riichinexus.system.json.RealtimeJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 发送到实时通道的标准事件载荷。
  *
  * 事件记录聚合类型与 ID、来源事件、可选操作者与接收人、展示文案和结构化数据，客户端据此选择刷新范围。
  */
final case class RealtimeEvent(
    id: String,
    eventType: RealtimeEventType,
    aggregateType: String,
    aggregateId: String,
    occurredAt: Instant,
    sourceEventType: RealtimeSourceEventType,
    actorId: Option[String] = None,
    recipientPlayerId: Option[String] = None,
    title: Option[String] = None,
    body: Option[String] = None,
    severity: Option[String] = None,
    actionUrl: Option[String] = None,
    data: Option[ujson.Value] = None
) 

object RealtimeEvent:
  given ReadWriter[RealtimeEvent] = macroRW
