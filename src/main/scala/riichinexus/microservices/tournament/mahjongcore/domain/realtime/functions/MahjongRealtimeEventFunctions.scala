package riichinexus.microservices.tournament.mahjongcore.domain.realtime.functions

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongPublicEventView
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongTableView
import riichinexus.microservices.tournament.objects.paifu.PaifuActionType
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.system.objects.`private`.AggregateType
import riichinexus.system.realtime.domain.RealtimeEventPayloadFunctions
import riichinexus.system.realtime.objects.{RealtimeEvent, RealtimeEventType, RealtimeSourceEventType}
import riichinexus.system.json.JsonCodecs.given

/** 组装麻将牌桌实时事件，供 API 在事务提交后发布到 SSE 通道。 */
private[tournament] object MahjongRealtimeEventFunctions:

  def actionAccepted(
      tableId: TableId,
      event: MahjongPublicEventView,
      occurredAt: Instant
  ): RealtimeEvent =
    RealtimeEvent(
      id = s"mahjong-action:${tableId.value}:${event.sequenceNo}",
      eventType = RealtimeEventType.MahjongActionAccepted,
      aggregateType = AggregateType.toString(AggregateType.MahjongTable),
      aggregateId = tableId.value,
      occurredAt = occurredAt,
      sourceEventType = sourceEventType(event.actionType),
      actorId = event.actor.map(_.value),
      actionUrl = Some(s"/tables/${tableId.value}"),
      data = Some(RealtimeEventPayloadFunctions.toJson(event))
    )

  def tableChanged(
      tableId: TableId,
      sourceEventType: RealtimeSourceEventType,
      table: MahjongTableView,
      actorId: Option[PlayerId],
      occurredAt: Instant
  ): RealtimeEvent =
    RealtimeEvent(
      id = s"mahjong-table:${tableId.value}:${RealtimeSourceEventType.toString(sourceEventType)}:${occurredAt.toEpochMilli}",
      eventType = RealtimeEventType.MahjongTableChanged,
      aggregateType = AggregateType.toString(AggregateType.MahjongTable),
      aggregateId = tableId.value,
      occurredAt = occurredAt,
      sourceEventType = sourceEventType,
      actorId = actorId.map(_.value),
      actionUrl = Some(s"/tables/${tableId.value}"),
      data = Some(RealtimeEventPayloadFunctions.toJson(table))
    )

  private def sourceEventType(actionType: PaifuActionType): RealtimeSourceEventType =
    actionType match
      case PaifuActionType.Draw       => RealtimeSourceEventType.Draw
      case PaifuActionType.Discard    => RealtimeSourceEventType.Discard
      case PaifuActionType.Chi        => RealtimeSourceEventType.Chi
      case PaifuActionType.Pon        => RealtimeSourceEventType.Pon
      case PaifuActionType.Kan        => RealtimeSourceEventType.Kan
      case PaifuActionType.Riichi     => RealtimeSourceEventType.Riichi
      case PaifuActionType.DoraReveal => RealtimeSourceEventType.DoraReveal
      case PaifuActionType.Win        => RealtimeSourceEventType.Win
      case PaifuActionType.DrawGame   => RealtimeSourceEventType.DrawGame
      case PaifuActionType.AddedKan   => RealtimeSourceEventType.AddedKan
      case PaifuActionType.ClosedKan  => RealtimeSourceEventType.ClosedKan
      case PaifuActionType.OpenKan    => RealtimeSourceEventType.OpenKan
