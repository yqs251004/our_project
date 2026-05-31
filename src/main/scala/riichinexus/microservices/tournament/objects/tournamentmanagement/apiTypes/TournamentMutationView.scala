package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.TournamentTableView
import upickle.default.*

final case class TournamentMutationView(
    tournament: TournamentDetailView,
    scheduledTables: Vector[TournamentTableView] = Vector.empty
) derives CanEqual

object TournamentMutationView:
  given ReadWriter[TournamentMutationView] = macroRW
