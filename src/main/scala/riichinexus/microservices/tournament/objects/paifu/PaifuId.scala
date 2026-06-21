package riichinexus.microservices.tournament.objects.paifu

/** 牌谱归档记录的稳定标识符。
  *
  * 牌谱 ID 独立于牌桌和对局记录，方便回放页、统计计算和归档接口引用同一份原始过程数据。
  */
final case class PaifuId(value: String)
