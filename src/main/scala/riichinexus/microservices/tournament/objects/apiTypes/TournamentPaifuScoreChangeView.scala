package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import riichinexus.microservices.tournament.domain.model.ScoreChange

final case class TournamentPaifuScoreChangeView(
    playerId: String,
    delta: Int
) derives CanEqual

object TournamentPaifuScoreChangeView:
  given ReadWriter[TournamentPaifuScoreChangeView] = macroRW

  def fromDomain(change: ScoreChange): TournamentPaifuScoreChangeView =
    TournamentPaifuScoreChangeView(
      playerId = change.playerId.value,
      delta = change.delta
    )
