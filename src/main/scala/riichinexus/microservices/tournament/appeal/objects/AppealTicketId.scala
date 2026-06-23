package riichinexus.microservices.tournament.appeal.objects

/** 申诉工单在系统内外传递的稳定标识符。
  *
  * 该值用于 URL、消息和持久化记录之间关联同一张申诉工单。
  */
final case class AppealTicketId(value: String)
