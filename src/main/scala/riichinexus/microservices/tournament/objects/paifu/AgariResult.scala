package riichinexus.microservices.tournament.objects.paifu

import riichinexus.microservices.player.objects.PlayerId

/** 一小局结束后的完整结算结果。
  *
  * 该类型兼容旧的单赢家字段和新的 `wins` 多赢家明细，同时保存分数变化、听牌玩家、宝牌信息和立直棒/本场结算。
  */
final case class AgariResult(
    outcome: HandOutcome,
    winner: Option[PlayerId] = None,
    target: Option[PlayerId] = None,
    han: Option[Int] = None,
    fu: Option[Int] = None,
    yaku: Vector[Yaku],
    points: Int,
    scoreChanges: Vector[ScoreChange],
    doraIndicators: Option[Vector[PaifuTile]] = None,
    uraDoraIndicators: Option[Vector[PaifuTile]] = None,
    uraDoraVisible: Option[Boolean] = None,
    tenpaiPlayerIds: Option[Vector[PlayerId]] = None,
    settlement: Option[RoundSettlement] = None,
    wins: Vector[AgariWinResult] = Vector.empty
)
