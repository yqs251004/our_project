package riichinexus.microservices.notification.api.`private`

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.notification.objects.{Notification, NotificationId}
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
import riichinexus.microservices.notification.tables.notifications.NotificationTable
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.realtime.objects.RealtimeEvent
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class CreateNotificationPrivateAPIMessage(
    request: CreateNotificationRequest
) extends APIMessage[Notification] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Notification] =
    for
      createdAt <- IO.realTimeInstant
      notification <- IO.blocking(createOrReuseNotification(context, createdAt))
      _ <- publishNotification(context, notification)
    yield notification

  private def createOrReuseNotification(
      context: ApiPlanContext,
      createdAt: Instant
  ): Notification =
    val recipientPlayerId = PlayerId(request.recipientPlayerId)
    val notification = Notification(
      id = NotificationId.next(),
      recipientPlayerId = recipientPlayerId,
      notificationType = request.notificationType,
      title = request.title,
      body = request.body,
      severity = request.severity.getOrElse("info"),
      sourceService = request.sourceService,
      sourceType = request.sourceType,
      sourceId = request.sourceId,
      actionUrl = request.actionUrl,
      createdAt = createdAt,
      expiresAt = request.expiresAt,
      objects = request.objects
    )
    NotificationTable.save(context.connection, notification)

  private def publishNotification(context: ApiPlanContext, notification: Notification): IO[Unit] =
    context.realtimeEventBus.publish(
      RealtimeEvent(
        id = notification.id.value,
        eventType = "NotificationCreated",
        aggregateType = "notification",
        aggregateId = notification.id.value,
        occurredAt = notification.createdAt,
        sourceEventType = notification.notificationType,
        recipientPlayerId = Some(notification.recipientPlayerId.value),
        title = Some(notification.title),
        body = Some(notification.body),
        severity = Some(notification.severity),
        actionUrl = notification.actionUrl
      )
    )
