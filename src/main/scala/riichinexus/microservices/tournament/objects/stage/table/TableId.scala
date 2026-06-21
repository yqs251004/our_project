package riichinexus.microservices.tournament.objects.stage.table

/** 赛事阶段内牌桌的稳定标识符。
  *
  * 牌桌 ID 会被实时对局、牌谱、对局记录、申诉和结算共同引用，因此独立值类型能避免和赛事或阶段 ID 混用。
  */
final case class TableId(value: String)
