package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

final case class TournamentWhitelistSummaryView(
    totalEntries: Int,
    clubCount: Int,
    playerCount: Int,
    clubIds: Vector[String],
    playerIds: Vector[String]
) derives CanEqual

object TournamentWhitelistSummaryView:
  given ReadWriter[TournamentWhitelistSummaryView] = macroRW
