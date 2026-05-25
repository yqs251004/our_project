package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{Table as _, TableSeat as _, StageStandingEntry as _, StageRankingSnapshot as _, StageAdvancementSnapshot as _, KnockoutBracketSlot as _, KnockoutBracketResult as _, KnockoutBracketMatch as _, KnockoutBracketRound as _, KnockoutBracketSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class TournamentWhitelistListAPIMessage(
    tournamentId: String,
    query: TournamentWhitelistQuery = TournamentWhitelistQuery()
) extends APIMessage[PagedResponse[TournamentWhitelistEntryView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentWhitelistEntryView]] =
    for
      resolved <- IO(resolveQuery)
      whitelist <- IO(listWhitelist(context, resolved))
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
  ): Vector[TournamentWhitelistEntryView] =
    context.support.tournamentModule.tables
      .findTournament(query.tournamentId)
      .map(_.whitelist
        .filter(entry => query.participantKind.forall(_ == entry.participantKind))
        .filter(entry => query.playerId.forall(id => entry.playerId.contains(id)))
        .filter(entry => query.clubId.forall(id => entry.clubId.contains(id)))
      )
      .getOrElse(throw NoSuchElementException(s"Tournament ${query.tournamentId.value} was not found"))
      .map(TournamentWhitelistEntryView.fromDomain)

  private def pagedResponse(
      items: Vector[TournamentWhitelistEntryView],
      query: ResolvedWhitelistQuery
  ): PagedResponse[TournamentWhitelistEntryView] =
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
