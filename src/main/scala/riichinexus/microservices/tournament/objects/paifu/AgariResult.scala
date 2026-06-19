package riichinexus.microservices.tournament.objects.paifu

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** AgariResult 表示前后端共享的一局结算结果，包含和牌/流局结果、分数变化、立直棒本场结算和多家和牌明细。 */
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
