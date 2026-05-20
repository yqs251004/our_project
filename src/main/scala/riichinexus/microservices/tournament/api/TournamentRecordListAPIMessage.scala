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

final case class TournamentRecordListAPIMessage(
    playerId: Option[String] = None,
    tournamentId: Option[String] = None,
    stageId: Option[String] = None,
    tableId: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[TournamentMatchRecordView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentMatchRecordView]] =
    IO {
      val query = MatchRecordListQuery(playerId.filter(_.nonEmpty).map(PlayerId(_)), tournamentId.filter(_.nonEmpty).map(TournamentId(_)), stageId.filter(_.nonEmpty).map(TournamentStageId(_)), tableId.filter(_.nonEmpty).map(TableId(_)))
      val records = context.support.tournamentModule.tables
        .listMatchRecords()
        .filter(record => query.playerId.forall(record.playerIds.contains))
        .filter(record => query.tournamentId.forall(_ == record.tournamentId))
        .filter(record => query.stageId.forall(_ == record.stageId))
        .filter(record => query.tableId.forall(_ == record.tableId))
        .sortBy(record => (record.generatedAt, record.id.value))
        .map(TournamentMatchRecordView.fromDomain)
      page(records, filters(playerId.filter(_.nonEmpty).map("playerId" -> _), tournamentId.filter(_.nonEmpty).map("tournamentId" -> _), stageId.filter(_.nonEmpty).map("stageId" -> _), tableId.filter(_.nonEmpty).map("tableId" -> _)))
    }

  private def page(items: Vector[TournamentMatchRecordView], appliedFilters: Map[String, String]): PagedResponse[TournamentMatchRecordView] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
