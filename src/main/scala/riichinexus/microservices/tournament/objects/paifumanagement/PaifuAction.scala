package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.domain.model.*

final case class PaifuAction(
    sequenceNo: Int,
    actor: Option[PlayerId] = None,
    actionType: PaifuActionType,
    tile: Option[PaifuTile] = None,
    shantenAfterAction: Option[Int] = None,
    handTilesAfterAction: Option[Vector[PaifuTile]] = None,
    revealedTiles: Vector[PaifuTile] = Vector.empty,
    note: Option[String] = None
) derives CanEqual
