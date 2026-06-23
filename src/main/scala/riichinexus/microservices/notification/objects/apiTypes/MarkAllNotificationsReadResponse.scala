package riichinexus.microservices.notification.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 批量标记通知已读后的操作结果。
  *
  * `updatedCount` 告诉前端本次实际更新了多少条通知，可用于刷新未读徽标或显示操作反馈。
  */
final case class MarkAllNotificationsReadResponse(
    updatedCount: Int
)

object MarkAllNotificationsReadResponse:
  given ReadWriter[MarkAllNotificationsReadResponse] = macroRW
