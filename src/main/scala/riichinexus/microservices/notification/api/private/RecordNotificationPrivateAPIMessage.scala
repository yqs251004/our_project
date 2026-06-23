package riichinexus.microservices.notification.api.`private`

import riichinexus.system.objects.`private`.AggregateType

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.notification.domain.functions.NotificationIdGenerator
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType}
import riichinexus.microservices.notification.tables.notifications.NotificationTable
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.realtime.objects.{RealtimeEvent, RealtimeEventType, RealtimeSourceEventType}
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 供后端服务记录单条通知。 */
final case class RecordNotificationPrivateAPIMessage(
    request: CreateNotificationRequest
) extends APIMessage[Notification]:

  override def plan(context: ApiPlanContext): IO[Notification] =
    for
      createdAt <- IO.realTimeInstant
      notification <- IO.blocking(buildNotification(createdAt))
      savedNotification <- saveNotification(context, notification)
      _ <- publishNotification(context, savedNotification)
    yield savedNotification

  private def buildNotification(createdAt: Instant): Notification =
    Notification(
      id = NotificationIdGenerator.notificationId(),
      recipientPlayerId = PlayerId(request.recipientPlayerId),
      notificationType = request.notificationType,
      title = request.title,
      body = request.body,
      severity = NotificationSeverity.toString(request.severity.getOrElse(NotificationSeverity.Info)),
      sourceService = NotificationSourceService.toString(request.sourceService),
      sourceType = NotificationSourceType.toString(request.sourceType),
      sourceId = request.sourceId,
      actionUrl = request.actionUrl,
      createdAt = createdAt,
      expiresAt = request.expiresAt,
      objects = request.objects
    )

  private def saveNotification(context: ApiPlanContext, notification: Notification): IO[Notification] =
    IO.blocking(NotificationTable.save(context.connection, notification))

  private def publishNotification(context: ApiPlanContext, notification: Notification): IO[Unit] =
    val event = RealtimeEvent(
      id = notification.id.value,
      eventType = RealtimeEventType.NotificationCreated,
      aggregateType = AggregateType.toString(AggregateType.Notification),
      aggregateId = notification.id.value,
      occurredAt = notification.createdAt,
      sourceEventType = RealtimeSourceEventType.fromString(notification.notificationType.toString),
      recipientPlayerId = Some(notification.recipientPlayerId.value),
      title = Some(notification.title),
      body = Some(notification.body),
      severity = Some(notification.severity),
      actionUrl = notification.actionUrl
    )
    context.afterCommit(context.realtimeEventBus.publish(event))
