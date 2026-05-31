package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.microservices.tournament.objects.{PaifuActionType}

import upickle.default.*

import riichinexus.microservices.tournament.domain.model.PaifuAction

final case class TournamentPaifuActionView(
    sequenceNo: Int,
    actor: Option[String],
    actionType: PaifuActionType,
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
      actionType = action.actionType,
      tile = action.tile,
      shantenAfterAction = action.shantenAfterAction,
      handTilesAfterAction = action.handTilesAfterAction,
      revealedTiles = action.revealedTiles,
      note = action.note
    )
