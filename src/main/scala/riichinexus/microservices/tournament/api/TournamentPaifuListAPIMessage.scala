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
import riichinexus.system.objects.apiTypes.PagedResponse
import upickle.default.*

final case class TournamentPaifuListAPIMessage(
    playerId: Option[String] = None,
    tournamentId: Option[String] = None,
    stageId: Option[String] = None,
    tableId: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[TournamentPaifuSummaryView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentPaifuSummaryView]] =
    IO {
      val query = PaifuListQuery(playerId.filter(_.nonEmpty).map(PlayerId(_)), tournamentId.filter(_.nonEmpty).map(TournamentId(_)), stageId.filter(_.nonEmpty).map(TournamentStageId(_)), tableId.filter(_.nonEmpty).map(TableId(_)))
      val paifus = context.support.tournamentModule.tables
        .listPaifus()
        .filter(paifu => query.playerId.forall(paifu.playerIds.contains))
        .filter(paifu => query.tournamentId.forall(_ == paifu.metadata.tournamentId))
        .filter(paifu => query.stageId.forall(_ == paifu.metadata.stageId))
        .filter(paifu => query.tableId.forall(_ == paifu.metadata.tableId))
        .sortBy(paifu => (paifu.metadata.recordedAt, paifu.id.value))
        .map(TournamentPaifuSummaryView.fromDomain)
      page(paifus, filters(playerId.filter(_.nonEmpty).map("playerId" -> _), tournamentId.filter(_.nonEmpty).map("tournamentId" -> _), stageId.filter(_.nonEmpty).map("stageId" -> _), tableId.filter(_.nonEmpty).map("tableId" -> _)))
    }

  private def page(items: Vector[TournamentPaifuSummaryView], appliedFilters: Map[String, String]): PagedResponse[TournamentPaifuSummaryView] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
