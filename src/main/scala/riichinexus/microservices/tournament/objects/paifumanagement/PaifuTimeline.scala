package riichinexus.microservices.tournament.objects.paifumanagement

/** PaifuTimeline 表示前后端共享的牌谱Timeline 数据结构，包含events。 */

final case class PaifuTimeline(
    events: Vector[PaifuAction]
)
