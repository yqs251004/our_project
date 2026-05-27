package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import riichinexus.microservices.tournament.domain.model.PaifuAction

final case class TournamentPaifuActionView(
    sequenceNo: Int,
    actor: Option[String],
    actionType: String,
    tile: Option[String],
    shantenAfterAction: Option[Int],
    handTilesAfterAction: Option[Vector[String]],
    revealedTiles: Vector[String],
    note: Option[String]
) derives CanEqual

object TournamentPaifuActionView:
  given ReadWriter[TournamentPaifuActionView] = macroRW

  def fromDomain(action: PaifuAction): TournamentPaifuActionView =
    TournamentPaifuActionView(
      sequenceNo = action.sequenceNo,
      actor = action.actor.map(_.value),
      actionType = action.actionType.toString,
      tile = action.tile,
      shantenAfterAction = action.shantenAfterAction,
      handTilesAfterAction = action.handTilesAfterAction,
      revealedTiles = action.revealedTiles,
      note = action.note
    )
