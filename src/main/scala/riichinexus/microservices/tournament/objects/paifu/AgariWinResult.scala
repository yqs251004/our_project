package riichinexus.microservices.tournament.objects.paifu

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** AgariWinResult 表示前后端共享的单个和牌结果，包含和牌者、放铳者、番符、役、点数和宝牌信息。 */
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
