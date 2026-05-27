package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import riichinexus.microservices.tournament.domain.model.FinalStanding

final case class TournamentPaifuFinalStandingView(
    playerId: String,
    seat: String,
    finalPoints: Int,
    placement: Int,
    uma: Double,
    oka: Double
) derives CanEqual

object TournamentPaifuFinalStandingView:
  given ReadWriter[TournamentPaifuFinalStandingView] = macroRW

  def fromDomain(standing: FinalStanding): TournamentPaifuFinalStandingView =
    TournamentPaifuFinalStandingView(
      playerId = standing.playerId.value,
      seat = standing.seat.toString,
      finalPoints = standing.finalPoints,
      placement = standing.placement,
      uma = standing.uma,
      oka = standing.oka
    )
