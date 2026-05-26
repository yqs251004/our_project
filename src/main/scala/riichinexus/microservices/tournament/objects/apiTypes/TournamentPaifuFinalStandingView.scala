package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.FinalStanding

final case class TournamentPaifuFinalStandingView(
    playerId: String,
    seat: String,
    finalPoints: Int,
    placement: Int,
    uma: Double,
    oka: Double
) derives CanEqual

object TournamentPaifuFinalStandingView:
  def fromDomain(standing: FinalStanding): TournamentPaifuFinalStandingView =
    TournamentPaifuFinalStandingView(
      playerId = standing.playerId.value,
      seat = standing.seat.toString,
      finalPoints = standing.finalPoints,
      placement = standing.placement,
      uma = standing.uma,
      oka = standing.oka
    )
