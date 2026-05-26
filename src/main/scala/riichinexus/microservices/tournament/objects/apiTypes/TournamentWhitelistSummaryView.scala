package riichinexus.microservices.tournament.objects.apiTypes

final case class TournamentWhitelistSummaryView(
    totalEntries: Int,
    clubCount: Int,
    playerCount: Int,
    clubIds: Vector[String],
    playerIds: Vector[String]
) derives CanEqual
