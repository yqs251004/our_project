package riichinexus.microservices.tournament.objects.matchrecord

/** 赛事对局记录的稳定标识符。
  *
  * 对局记录独立于牌谱和牌桌存在，用于结算、榜单、公开近期战绩和申诉流程共同引用同一场结果。
  */
final case class MatchRecordId(value: String)
