package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class TournamentStageTablesAPIMessage(
    tournamentId: String,
    stageId: String,
    status: Option[String] = None,
    roundNumber: Option[Int] = None,
    playerId: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[TournamentTableView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentTableView]] =
    IO {
      val query = StageTableQuery(
        status = status.filter(_.nonEmpty).map(TableStatus.valueOf),
        roundNumber = roundNumber,
        playerId = playerId.filter(_.nonEmpty).map(PlayerId(_))
      )
      val tables = context.support.tournamentModule.tables
        .listStageTables(TournamentId(tournamentId), TournamentStageId(stageId), query)
        .map(TournamentTableView.fromDomain)
      page(tables, filters(status.filter(_.nonEmpty).map("status" -> _), roundNumber.map(value => "roundNumber" -> value.toString), playerId.filter(_.nonEmpty).map("playerId" -> _)))
    }

  private def page(items: Vector[TournamentTableView], appliedFilters: Map[String, String]): PagedResponse[TournamentTableView] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
