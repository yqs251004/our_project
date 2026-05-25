package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{Table as _, TableSeat as _, StageStandingEntry as _, StageRankingSnapshot as _, StageAdvancementSnapshot as _, KnockoutBracketSlot as _, KnockoutBracketResult as _, KnockoutBracketMatch as _, KnockoutBracketRound as _, KnockoutBracketSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class TournamentStageTablesAPIMessage(
    tournamentId: String,
    stageId: String,
    query: StageTableQuery = StageTableQuery()
) extends APIMessage[PagedResponse[TournamentTableView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentTableView]] =
    for
      resolved <- IO(resolveQuery)
      tables <- IO(listStageTables(context, resolved))
    yield PagedResponse.fromItems(tables, resolved.query.limit, resolved.query.offset, resolved.appliedFilters)(TournamentTableView.fromDomain)

  private def resolveQuery: ResolvedStageTablesQuery =
    ResolvedStageTablesQuery(
      tournamentId = TournamentId(tournamentId),
      stageId = TournamentStageId(stageId),
      query = query,
      appliedFilters = Vector(
        query.status.map(value => "status" -> value.toString),
        query.roundNumber.map(value => "roundNumber" -> value.toString),
        query.playerId.map(value => "playerId" -> value.value)
      ).flatten.toMap
    )

  private def listStageTables(
      context: ApiPlanContext,
      resolved: ResolvedStageTablesQuery
  ): Vector[Table] =
    context.support.tournamentModule.tables
      .listStageTables(resolved.tournamentId, resolved.stageId, resolved.query)

  private final case class ResolvedStageTablesQuery(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      query: StageTableQuery,
      appliedFilters: Map[String, String]
  )
