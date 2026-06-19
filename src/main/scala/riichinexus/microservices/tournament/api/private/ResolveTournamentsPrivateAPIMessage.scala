package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.domain.functions.TournamentPrivateViewFunctions
import riichinexus.microservices.tournament.objects.`private`.competition.TournamentPrivateView
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
/** 供后端服务按 id 批量读取赛事 private read model。 */
final case class ResolveTournamentsPrivateAPIMessage(
    tournamentIds: Vector[TournamentId]
) extends APIMessage[Vector[TournamentPrivateView]]:

  override def plan(context: ApiPlanContext): IO[Vector[TournamentPrivateView]] =
    for
      tournaments <- IO.blocking(resolveTournaments(context))
    yield tournaments.map(TournamentPrivateViewFunctions.fromTournament)

  private def resolveTournaments(context: ApiPlanContext): Vector[Tournament] =
    val distinctIds = tournamentIds.distinct
    val prefetched = TournamentTable.findByIds(context.connection, distinctIds)
      .map(tournament => tournament.id -> tournament)
      .toMap

    distinctIds.flatMap { id =>
      prefetched.get(id).orElse(TournamentTable.findById(context.connection, id))
    }
