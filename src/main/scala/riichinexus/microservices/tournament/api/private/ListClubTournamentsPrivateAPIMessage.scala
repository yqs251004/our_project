package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.functions.TournamentPrivateViewFunctions
import riichinexus.microservices.tournament.objects.`private`.competition.TournamentPrivateView
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
/** 供后端服务读取某俱乐部关联的赛事 private read model 列表。 */
final case class ListClubTournamentsPrivateAPIMessage(
    clubId: ClubId
) extends APIMessage[Vector[TournamentPrivateView]]:

  override def plan(context: ApiPlanContext): IO[Vector[TournamentPrivateView]] =
    for
      tournaments <- IO.blocking(TournamentTable.findByClub(context.connection, clubId))
    yield tournaments.map(TournamentPrivateViewFunctions.fromTournament)
