package riichinexus.microservices.tournament.objects.competition.apiTypes

import riichinexus.microservices.tournament.objects.stage.table.apiTypes.TournamentTableView
import upickle.default.{ReadWriter, macroRW}

/** TournamentMutationView 表示赛事Mutation视图 的前端展示视图。 */

final case class TournamentMutationView(
    tournament: TournamentDetailView,
    scheduledTables: Vector[TournamentTableView] = Vector.empty
)

object TournamentMutationView:
  given ReadWriter[TournamentMutationView] = macroRW
