package riichinexus.microservices.tournament.objects.stage.lineup

/** 俱乐部阶段阵容提交记录的稳定标识符。
  *
  * 赛事阶段可以接收多次阵容提交，独立 ID 让后台能够追踪哪一次提交被用于排桌。
  */
final case class LineupSubmissionId(value: String)
