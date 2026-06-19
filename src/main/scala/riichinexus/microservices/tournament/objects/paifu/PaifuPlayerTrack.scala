package riichinexus.microservices.tournament.objects.paifu

/** PaifuPlayerTrack 表示前后端共享的牌谱玩家Track 数据结构，包含events。 */

final case class PaifuPlayerTrack(
    events: Vector[PaifuAction]
)
