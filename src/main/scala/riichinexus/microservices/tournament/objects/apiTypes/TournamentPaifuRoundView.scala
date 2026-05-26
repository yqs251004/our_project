package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.KyokuRecord

final case class TournamentPaifuRoundView(
    descriptor: TournamentPaifuRoundDescriptorView,
    initialHands: Map[String, Vector[String]],
    actions: Vector[TournamentPaifuActionView],
    result: TournamentPaifuRoundResultView
) derives CanEqual

object TournamentPaifuRoundView:
  def fromDomain(round: KyokuRecord): TournamentPaifuRoundView =
    TournamentPaifuRoundView(
      descriptor = TournamentPaifuRoundDescriptorView.fromDomain(round.descriptor),
      initialHands = round.initialHands.map { case (playerId, tiles) => playerId.value -> tiles },
      actions = round.actions.map(TournamentPaifuActionView.fromDomain),
      result = TournamentPaifuRoundResultView.fromDomain(round.result)
    )
