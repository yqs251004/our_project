package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class TournamentSettlementListAPIMessage(
    tournamentId: String,
    query: TournamentSettlementQuery = TournamentSettlementQuery()
) extends APIMessage[PagedResponse[TournamentSettlementView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentSettlementView]] =
    for
      resolved <- IO(resolveQuery)
      settlements <- IO(listSettlements(context, resolved))
    yield page(settlements.map(TournamentSettlementView.fromDomain), resolved)

  private def resolveQuery: ResolvedSettlementListQuery =
    ResolvedSettlementListQuery(
      tournamentId = TournamentId(tournamentId),
      query = query,
      appliedFilters = filters(
        query.stageId.map(value => "stageId" -> value.value),
        query.status.map(value => "status" -> value.toString),
        query.championId.map(value => "championId" -> value.value)
      )
    )

  private def listSettlements(
      context: ApiPlanContext,
      resolved: ResolvedSettlementListQuery
  ): Vector[TournamentSettlementSnapshot] =
    TournamentSettlementTable
      .findByTournament(context.connection, resolved.tournamentId)
      .filter(snapshot => resolved.query.stageId.forall(_ == snapshot.stageId))
      .filter(snapshot => resolved.query.status.forall(_ == snapshot.status))
      .filter(snapshot => resolved.query.championId.forall(_ == snapshot.championId))
      .sortBy(snapshot => (snapshot.generatedAt, snapshot.revision))

  private def page(
      items: Vector[TournamentSettlementView],
      resolved: ResolvedSettlementListQuery
  ): PagedResponse[TournamentSettlementView] =
    val resolvedLimit = resolved.query.limit.getOrElse(20)
    val resolvedOffset = resolved.query.offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, resolved.appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  private final case class ResolvedSettlementListQuery(
      tournamentId: TournamentId,
      query: TournamentSettlementQuery,
      appliedFilters: Map[String, String]
  )
