package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.TournamentTableView
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentId
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.TournamentMutationView
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class GetTournamentMutationViewPrivateAPIMessage(
    tournamentId: TournamentId,
    scheduledTables: Vector[Table] = Vector.empty
) extends APIMessage[Option[TournamentMutationView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[TournamentMutationView]] =
    GetTournamentDetailViewPrivateAPIMessage(tournamentId).plan(context).map(_.map { detail =>
      TournamentMutationView(
        tournament = detail,
        scheduledTables = scheduledTables
          .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
          .map(TournamentTableView.fromDomain)
      )
    })
