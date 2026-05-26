package riichinexus.microservices.publicquery.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.TournamentId
import riichinexus.microservices.publicquery.objects.apiTypes.PublicTournamentDetailView
import riichinexus.microservices.publicquery.domain.PublicTournamentViews
import upickle.default.*

final case class GetPublicTournamentAPIMessage(
    tournamentId: String
) extends APIMessage[PublicTournamentDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PublicTournamentDetailView] =
    for
      id <- IO(TournamentId(tournamentId))
      tournament <- IO(findPublicTournament(context, id))
    yield tournament

  private def findPublicTournament(
      context: ApiPlanContext,
      tournamentId: TournamentId
  ): PublicTournamentDetailView =
    PublicTournamentViews
      .detail(context.connection, context.support.tournamentModule, tournamentId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
