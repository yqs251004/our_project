package riichinexus.microservices.tournament.api.paifu
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.domain.paifu.functions.PaifuFunctions

import riichinexus.microservices.tournament.objects.paifu.Paifu
import riichinexus.microservices.tournament.objects.paifu.apiTypes.{PaifuListQuery}
import riichinexus.microservices.tournament.objects.paifu.{PaifuRoundScoreChanges, PaifuSummary}

import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出赛事牌谱摘要。 */
final case class TournamentPaifuListAPIMessage(
  query: PaifuListQuery = PaifuListQuery()
) extends APIMessage[PagedResponse[PaifuSummary]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PaifuSummary]] =
    val appliedFilters = filters(
      query.playerId.map(value => QueryFilterField.toString(QueryFilterField.PlayerId) -> value.value),
      query.tournamentId.map(value => QueryFilterField.toString(QueryFilterField.TournamentId) -> value.value),
      query.stageId.map(value => QueryFilterField.toString(QueryFilterField.StageId) -> value.value),
      query.tableId.map(value => QueryFilterField.toString(QueryFilterField.TableId) -> value.value)
    )
    for
      paifus <- IO.blocking(listPaifus(context))
    yield PagedResponse.fromItems(paifus, query.limit, query.offset, appliedFilters)(identity)

  private def listPaifus(
      context: ApiPlanContext
  ): Vector[PaifuSummary] =
    PaifuTable
      .findAll(context.connection)
      .filter(paifu => query.playerId.forall(PaifuFunctions.playerIds(paifu).contains))
      .filter(paifu => query.tournamentId.forall(_ == paifu.metadata.tournamentId))
      .filter(paifu => query.stageId.forall(_ == paifu.metadata.stageId))
      .filter(paifu => query.tableId.forall(_ == paifu.metadata.tableId))
      .sortBy(paifu => (paifu.metadata.recordedAt, paifu.id.value))
      .map(toSummary)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  private def toSummary(paifu: Paifu): PaifuSummary =
    PaifuSummary(
      paifuId = paifu.id.value,
      tableId = paifu.metadata.tableId.value,
      tournamentId = paifu.metadata.tournamentId.value,
      stageId = paifu.metadata.stageId.value,
      recordedAt = paifu.metadata.recordedAt.toString,
      source = paifu.metadata.source,
      matchRecordId = paifu.metadata.matchRecordId.map(_.value),
      totalHands = PaifuFunctions.totalHands(paifu),
      playerIds = PaifuFunctions.playerIds(paifu).map(_.value),
      finalStandings = paifu.finalStandings,
      roundScoreChanges = paifu.rounds.map(round =>
        PaifuRoundScoreChanges(
          descriptor = round.descriptor,
          scoreChanges = round.result.scoreChanges
        )
      )
    )
