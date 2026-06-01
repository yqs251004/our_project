package riichinexus.microservices.tournament.api

import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentParticipantKind, TournamentWhitelistEntry}

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

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
