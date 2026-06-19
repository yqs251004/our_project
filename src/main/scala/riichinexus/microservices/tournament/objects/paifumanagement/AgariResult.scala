package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** AgariResult 表示前后端共享的Agari结果 数据结构，包含winner、target、han、fu、yaku、点数等。 */

final case class AgariWinResult(
    winner: PlayerId,
    target: Option[PlayerId] = None,
    han: Option[Int] = None,
    fu: Option[Int] = None,
    yaku: Vector[Yaku] = Vector.empty,
    points: Int = 0,
    doraIndicators: Option[Vector[PaifuTile]] = None,
    uraDoraIndicators: Option[Vector[PaifuTile]] = None,
    uraDoraVisible: Option[Boolean] = None
)

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
