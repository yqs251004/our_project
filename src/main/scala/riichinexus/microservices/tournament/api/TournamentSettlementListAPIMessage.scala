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

final case class TournamentSettlementListAPIMessage(
    tournamentId: String,
    stageId: Option[String] = None,
    status: Option[String] = None,
    championId: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[TournamentSettlementView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentSettlementView]] =
    IO {
      val query = TournamentSettlementQuery(
        stageId = stageId.filter(_.nonEmpty).map(TournamentStageId(_)),
        status = status.filter(_.nonEmpty).map(TournamentSettlementStatus.valueOf),
        championId = championId.filter(_.nonEmpty).map(PlayerId(_))
      )
      val settlements = context.support.tournamentModule.tables
        .listSettlements(TournamentId(tournamentId), query)
        .map(TournamentSettlementView.fromDomain)
      page(settlements, filters(stageId.filter(_.nonEmpty).map("stageId" -> _), status.filter(_.nonEmpty).map("status" -> _), championId.filter(_.nonEmpty).map("championId" -> _)))
    }

  private def page(items: Vector[TournamentSettlementView], appliedFilters: Map[String, String]): PagedResponse[TournamentSettlementView] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
