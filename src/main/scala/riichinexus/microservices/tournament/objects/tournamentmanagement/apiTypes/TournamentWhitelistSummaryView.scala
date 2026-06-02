package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import upickle.default.*

final case class TournamentWhitelistSummaryView(
    totalEntries: Int,
    clubCount: Int,
    playerCount: Int,
    clubIds: Vector[String],
    playerIds: Vector[String]
)

object TournamentWhitelistSummaryView:
  given ReadWriter[TournamentWhitelistSummaryView] = macroRW
