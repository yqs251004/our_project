package riichinexus.microservices.tournament.objects.paifu

/** Yaku 表示前后端共享的役种 数据结构，包含类型、han。 */

final case class Yaku(
    kind: MahjongYakuKind,
    han: Int
)
