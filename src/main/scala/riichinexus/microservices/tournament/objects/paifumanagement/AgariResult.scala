package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.domain.model.*

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
    settlement: Option[RoundSettlement] = None
) derives CanEqual
