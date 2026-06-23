package riichinexus.microservices.tournament.api.competition
import riichinexus.microservices.tournament.objects.competition.TournamentWhitelistEntry

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.TournamentId


import riichinexus.microservices.tournament.objects.competition.apiTypes.TournamentWhitelistQuery

import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出赛事白名单。 */
final case class TournamentWhitelistListAPIMessage(
    tournamentId: String,
    query: TournamentWhitelistQuery = TournamentWhitelistQuery()
) extends APIMessage[PagedResponse[TournamentWhitelistEntry]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentWhitelistEntry]] =
    for
      requestedTournamentId <- IO.blocking(TournamentId(tournamentId))
      appliedFilters = filters(
        query.participantKind.map(value => QueryFilterField.toString(QueryFilterField.ParticipantKind) -> value.toString),
        query.playerId.map(value => QueryFilterField.toString(QueryFilterField.PlayerId) -> value.value),
        query.clubId.map(value => QueryFilterField.toString(QueryFilterField.ClubId) -> value.value)
      )
      whitelist <- IO.blocking(listWhitelist(context, requestedTournamentId))
    yield PagedResponse.fromItems(whitelist, query.limit, query.offset, appliedFilters)(identity)

  private def listWhitelist(
      context: ApiPlanContext,
      tournamentId: TournamentId
  ): Vector[TournamentWhitelistEntry] =
    TournamentTable
      .findById(context.connection, tournamentId)
      .map(_.whitelist
        .filter(entry => query.participantKind.forall(_ == entry.participantKind))
        .filter(entry => query.playerId.forall(id => entry.playerId.contains(id)))
        .filter(entry => query.clubId.forall(id => entry.clubId.contains(id)))
      )
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
