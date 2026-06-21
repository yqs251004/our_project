package riichinexus.microservices.tournament.objects.identity

/** 赛事聚合的稳定标识符。
  *
  * 该值用于赛事详情、报名、阶段配置和结算流程之间引用同一场赛事。
  */
final case class TournamentId(value: String)

/** 赛事阶段的稳定标识符。
  *
  * 阶段 ID 与赛事 ID 分属不同空间，独立值类型可以防止阶段查询、排桌和结算逻辑混用两个层级的标识。
  */
final case class TournamentStageId(value: String)
