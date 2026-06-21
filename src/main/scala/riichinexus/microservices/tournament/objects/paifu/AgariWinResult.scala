package riichinexus.microservices.tournament.objects.paifu

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** 一次和牌中的单个赢家结果。
  *
  * 多家荣和时每个赢家各有一条记录；自摸或单家荣和也可用它表达番符、役、点数、宝牌和里宝牌可见性。
  */
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
