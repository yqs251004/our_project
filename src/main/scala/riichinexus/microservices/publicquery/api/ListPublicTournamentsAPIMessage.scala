package riichinexus.microservices.publicquery.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.TournamentStatus
import riichinexus.microservices.publicquery.objects.apiTypes.PublicTournamentSummaryView
import riichinexus.microservices.publicquery.domain.PublicDirectoryQueries
import riichinexus.microservices.publicquery.domain.PublicTournamentViews
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListPublicTournamentsAPIMessage(
    status: Option[String] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicTournamentSummaryView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicTournamentSummaryView]] =
    for
      query <- IO(resolveQuery(context))
      tournaments <- IO(listTournaments(context, query))
      summaries <- IO(PublicTournamentViews.summaries(context.connection, context.support.tournamentModule, tournaments))
    yield PagedResponse.fromItems(summaries, limit, offset, query.appliedFilters)(identity)

  private def resolveQuery(context: ApiPlanContext): ResolvedPublicTournamentsQuery =
    ResolvedPublicTournamentsQuery(
      status = status.filter(_.nonEmpty).map(
        context.support.parseEnum("status", _)(TournamentStatus.valueOf)
      ),
      organizer = organizer.filter(_.nonEmpty),
      appliedFilters = Vector(
        status.filter(_.nonEmpty).map("status" -> _),
        organizer.filter(_.nonEmpty).map("organizer" -> _)
      ).flatten.toMap
    )

  private def listTournaments(
      context: ApiPlanContext,
      query: ResolvedPublicTournamentsQuery
  ) =
    PublicDirectoryQueries
      .listPublicTournaments(
        connection = context.connection,
        status = query.status,
        organizer = query.organizer
      )
      .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))

  private final case class ResolvedPublicTournamentsQuery(
      status: Option[TournamentStatus],
      organizer: Option[String],
      appliedFilters: Map[String, String]
  )
