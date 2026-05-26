package riichinexus.microservices.tournament.objects.apiTypes

final case class TournamentMutationView(
    tournament: TournamentDetailView,
    scheduledTables: Vector[TournamentTableView] = Vector.empty
) derives CanEqual
