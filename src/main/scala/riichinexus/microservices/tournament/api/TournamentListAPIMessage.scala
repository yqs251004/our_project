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

final case class TournamentListAPIMessage(
    status: Option[String] = None,
    adminId: Option[String] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[TournamentSummaryView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentSummaryView]] =
    IO {
      val query = TournamentListQuery(
        status = status.filter(_.nonEmpty).map(TournamentStatus.valueOf),
        adminId = adminId.filter(_.nonEmpty).map(PlayerId(_)),
        organizer = organizer.filter(_.nonEmpty)
      )
      val tournaments = context.support.tournamentModule.tables
        .listTournaments(
          status = query.status,
          adminId = query.adminId,
          organizer = query.organizer
        )
        .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))
        .map(TournamentSummaryView.fromDomain)
      page(tournaments, filters(status.filter(_.nonEmpty).map("status" -> _), adminId.filter(_.nonEmpty).map("adminId" -> _), organizer.filter(_.nonEmpty).map("organizer" -> _)))
    }

  private def page(items: Vector[TournamentSummaryView], appliedFilters: Map[String, String]): PagedResponse[TournamentSummaryView] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
