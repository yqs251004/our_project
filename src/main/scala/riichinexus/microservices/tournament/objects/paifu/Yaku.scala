package riichinexus.microservices.tournament.objects.paifu

/** 一项和牌役种及其番数。
  *
  * 役种类型说明是什么役，`han` 保存该役在当前上下文下的番数，包含宝牌和里宝牌等可变番来源。
  */
final case class Yaku(
    kind: MahjongYakuKind,
    han: Int
)
