package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.ScoreChange

final case class TournamentPaifuScoreChangeView(
    playerId: String,
    delta: Int
) derives CanEqual

object TournamentPaifuScoreChangeView:
  def fromDomain(change: ScoreChange): TournamentPaifuScoreChangeView =
    TournamentPaifuScoreChangeView(
      playerId = change.playerId.value,
      delta = change.delta
    )
