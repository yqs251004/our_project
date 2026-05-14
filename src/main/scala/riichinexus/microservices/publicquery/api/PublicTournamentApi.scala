package riichinexus.microservices.publicquery.api

import riichinexus.domain.model.{Tournament, TournamentId}
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.{
  PublicTournamentDetailResponse,
  PublicTournamentSummaryResponse
}
import riichinexus.microservices.publicquery.objects.PublicTournamentQuery
import riichinexus.microservices.publicquery.tables.PublicQueryTables
import riichinexus.microservices.tournament.api.TournamentViewAssembler

object PublicTournamentApi:

  private def listTournaments(
      tables: PublicQueryTables,
      query: PublicTournamentQuery
  ): Vector[Tournament] =
    tables.listPublicTournaments(
      status = query.status,
      organizer = query.organizer
    )
      .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))

  def listTournamentSummaries(
      tables: PublicQueryTables,
      views: TournamentViewAssembler,
      query: PublicTournamentQuery
  ): Vector[PublicTournamentSummaryResponse] =
    views.buildPublicTournamentSummaryViews(listTournaments(tables, query))

  def detail(
      views: TournamentViewAssembler,
      tournamentId: TournamentId
  ): Option[PublicTournamentDetailResponse] =
    views.buildPublicTournamentDetailView(tournamentId)
