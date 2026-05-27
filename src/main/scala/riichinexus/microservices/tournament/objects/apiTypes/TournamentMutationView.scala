package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

final case class TournamentMutationView(
    tournament: TournamentDetailView,
    scheduledTables: Vector[TournamentTableView] = Vector.empty
) derives CanEqual

object TournamentMutationView:
  given ReadWriter[TournamentMutationView] = macroRW
