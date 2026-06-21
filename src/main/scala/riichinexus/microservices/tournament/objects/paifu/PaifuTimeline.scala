package riichinexus.microservices.tournament.objects.paifu

/** 一小局或整副牌谱的全局事件时间线。
  *
  * 事件按 `sequenceNo` 排序，前端回放和后端统计都从同一条时间线还原对局过程。
  */
final case class PaifuTimeline(
    events: Vector[PaifuAction]
)
