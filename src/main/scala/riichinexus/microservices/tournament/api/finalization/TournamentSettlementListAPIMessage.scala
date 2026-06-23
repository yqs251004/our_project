package riichinexus.microservices.tournament.api.finalization
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.domain.finalization.model.TournamentSettlementSnapshot

import riichinexus.microservices.tournament.objects.finalization.apiTypes.{TournamentSettlementQuery}
import riichinexus.microservices.tournament.objects.finalization.{TournamentSettlementView}

import riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出赛事结算记录。 */
final case class TournamentSettlementListAPIMessage(
    tournamentId: String,
    query: TournamentSettlementQuery = TournamentSettlementQuery()
) extends APIMessage[PagedResponse[TournamentSettlementView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentSettlementView]] =
    for
      requestedTournamentId <- IO.blocking(TournamentId(tournamentId))
      appliedFilters = filters(
        query.stageId.map(value => QueryFilterField.toString(QueryFilterField.StageId) -> value.value),
        query.status.map(value => QueryFilterField.toString(QueryFilterField.Status) -> value.toString),
        query.championId.map(value => QueryFilterField.toString(QueryFilterField.ChampionId) -> value.value)
      )
      settlements <- IO.blocking(listSettlements(context, requestedTournamentId))
      settlementViews = settlements.map(TournamentViewFunctions.settlementView)
    yield PagedResponse.fromItems(settlementViews, query.limit, query.offset, appliedFilters)(identity)

  private def listSettlements(
      context: ApiPlanContext,
      tournamentId: TournamentId
  ): Vector[TournamentSettlementSnapshot] =
    TournamentSettlementTable
      .findByTournament(context.connection, tournamentId)
      .filter(snapshot => query.stageId.forall(_ == snapshot.stageId))
      .filter(snapshot => query.status.forall(_ == snapshot.status))
      .filter(snapshot => query.championId.forall(_ == snapshot.championId))
      .sortBy(snapshot => (snapshot.generatedAt, snapshot.revision))

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
