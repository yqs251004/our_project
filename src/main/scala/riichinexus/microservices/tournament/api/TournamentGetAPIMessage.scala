package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import upickle.default.*

final case class TournamentGetAPIMessage(tournamentId: String) extends APIMessage[TournamentDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentDetailView] =
    for
      id <- IO(TournamentId(tournamentId))
      view <- IO(resolveDetailView(context, id))
    yield view

  private def resolveDetailView(context: ApiPlanContext, tournamentId: TournamentId): TournamentDetailView =
    TournamentOperationViewAssembler.detailView(context.connection, context.support.tournamentModule, tournamentId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
