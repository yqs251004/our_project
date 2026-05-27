package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class PaifuAction(
    sequenceNo: Int,
    actor: Option[PlayerId] = None,
    actionType: PaifuActionType,
    tile: Option[String] = None,
    shantenAfterAction: Option[Int] = None,
    handTilesAfterAction: Option[Vector[String]] = None,
    revealedTiles: Vector[String] = Vector.empty,
    note: Option[String] = None
) derives CanEqual:
  require(sequenceNo >= 1, "Paifu action sequence number must be positive")
  shantenAfterAction.foreach { value =>
    require(value >= -1 && value <= 13, "Shanten value must be between -1 and 13")
  }
  handTilesAfterAction.foreach { tiles =>
    require(tiles.nonEmpty, "Paifu action hand snapshot cannot be empty when provided")
    require(tiles.size >= 1 && tiles.size <= 14, "Paifu action hand snapshot must contain between 1 and 14 tiles")
  }
  require(revealedTiles.forall(_.trim.nonEmpty), "Paifu action revealed tiles cannot contain blank entries")

