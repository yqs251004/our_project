package riichinexus.microservices.tournament.api

import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentParticipantKind, TournamentWhitelistEntry}

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentId


import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.TournamentWhitelistQuery

import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.objects.PagedResponse
import upickle.default.ReadWriter

/** 列出赛事白名单。 */
final case class TournamentWhitelistListAPIMessage(
    tournamentId: String,
    query: TournamentWhitelistQuery = TournamentWhitelistQuery()
) extends APIMessage[PagedResponse[TournamentWhitelistEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentWhitelistEntry]] =
    for
      resolved <- IO.blocking(resolveQuery)
      whitelist <- IO.blocking(listWhitelist(context, resolved))
    yield pagedResponse(whitelist, resolved)

  private def resolveQuery: ResolvedWhitelistQuery =
    ResolvedWhitelistQuery(
      tournamentId = TournamentId(tournamentId),
      participantKind = query.participantKind,
      playerId = query.playerId,
      clubId = query.clubId,
      limit = query.limit.getOrElse(20),
      offset = query.offset.getOrElse(0),
      appliedFilters = filters(
        query.participantKind.map(value => "participantKind" -> value.toString),
        query.playerId.map(value => "playerId" -> value.value),
        query.clubId.map(value => "clubId" -> value.value)
      )
    )

  private def listWhitelist(
      context: ApiPlanContext,
      query: ResolvedWhitelistQuery
  ): Vector[TournamentWhitelistEntry] =
    TournamentTable
      .findById(context.connection, query.tournamentId)
      .map(_.whitelist
        .filter(entry => query.participantKind.forall(_ == entry.participantKind))
        .filter(entry => query.playerId.forall(id => entry.playerId.contains(id)))
        .filter(entry => query.clubId.forall(id => entry.clubId.contains(id)))
      )
      .getOrElse(throw NoSuchElementException(s"Tournament ${query.tournamentId.value} was not found"))

  private def pagedResponse(
      items: Vector[TournamentWhitelistEntry],
      query: ResolvedWhitelistQuery
  ): PagedResponse[TournamentWhitelistEntry] =
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val pageItems = items.slice(query.offset, query.offset + boundedLimit)
    PagedResponse(
      pageItems,
      items.size,
      boundedLimit,
      query.offset,
      query.offset + pageItems.size < items.size,
      query.appliedFilters
    )

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  private final case class ResolvedWhitelistQuery(
      tournamentId: TournamentId,
      participantKind: Option[TournamentParticipantKind],
      playerId: Option[PlayerId],
      clubId: Option[ClubId],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
