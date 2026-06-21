package riichinexus.microservices.tournament.objects.paifu

/** 从全局牌谱中切出的单个玩家事件轨迹。
  *
  * 它用于展示玩家视角回放和计算个人行为统计，事件仍保持原始牌谱动作结构。
  */
final case class PaifuPlayerTrack(
    events: Vector[PaifuAction]
)
