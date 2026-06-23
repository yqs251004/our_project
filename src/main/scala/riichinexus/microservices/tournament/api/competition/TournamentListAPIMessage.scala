package riichinexus.microservices.tournament.api.competition
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.microservices.tournament.objects.competition.{TournamentSummaryView}

import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出管理视角的赛事。 */
final case class TournamentListAPIMessage(
    status: Option[String] = None,
    adminId: Option[String] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[TournamentSummaryView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentSummaryView]] =
    for
      statusFilter <- IO.blocking(status.filter(_.nonEmpty).map(TournamentStatus.valueOf))
      adminFilter <- IO.blocking(adminId.filter(_.nonEmpty).map(PlayerId(_)))
      organizerFilter = organizer.filter(_.nonEmpty)
      appliedFilters = filters(
        status.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.Status) -> _),
        adminId.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.AdminId) -> _),
        organizerFilter.map(QueryFilterField.toString(QueryFilterField.Organizer) -> _)
      )
      tournaments <- IO.blocking(listTournaments(context, statusFilter, adminFilter, organizerFilter))
    yield PagedResponse.fromItems(tournaments, limit, offset, appliedFilters)(identity)

  private def listTournaments(
      context: ApiPlanContext,
      status: Option[TournamentStatus],
      adminId: Option[PlayerId],
      organizer: Option[String]
  ): Vector[TournamentSummaryView] =
    TournamentTable
      .findFiltered(
        context.connection,
        status = status,
        adminId = adminId,
        organizer = organizer
      )
      .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))
      .map(TournamentViewFunctions.tournamentSummaryView)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
