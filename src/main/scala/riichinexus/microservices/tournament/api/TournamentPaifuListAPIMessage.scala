package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.tournament.domain.paifumanagement.functions.PaifuFunctions
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifumanagement.Paifu
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class TournamentPaifuListAPIMessage(
    query: PaifuListQuery = PaifuListQuery()
) extends APIMessage[PagedResponse[PaifuSummary]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PaifuSummary]] =
    for
      resolved <- IO.blocking(resolveQuery)
      paifus <- IO.blocking(listPaifus(context, resolved))
    yield pagedResponse(paifus, resolved)

  private def resolveQuery: ResolvedPaifuListQuery =
    ResolvedPaifuListQuery(
      query = query,
      limit = query.limit.getOrElse(20),
      offset = query.offset.getOrElse(0),
      appliedFilters = filters(
        query.playerId.map(value => "playerId" -> value.value),
        query.tournamentId.map(value => "tournamentId" -> value.value),
        query.stageId.map(value => "stageId" -> value.value),
        query.tableId.map(value => "tableId" -> value.value)
      )
    )

  private def listPaifus(
      context: ApiPlanContext,
      resolved: ResolvedPaifuListQuery
  ): Vector[PaifuSummary] =
    PaifuTable
      .findAll(context.connection)
      .filter(paifu => resolved.query.playerId.forall(PaifuFunctions.playerIds(paifu).contains))
      .filter(paifu => resolved.query.tournamentId.forall(_ == paifu.metadata.tournamentId))
      .filter(paifu => resolved.query.stageId.forall(_ == paifu.metadata.stageId))
      .filter(paifu => resolved.query.tableId.forall(_ == paifu.metadata.tableId))
      .sortBy(paifu => (paifu.metadata.recordedAt, paifu.id.value))
      .map(toSummary)

  private def pagedResponse(
      items: Vector[PaifuSummary],
      query: ResolvedPaifuListQuery
  ): PagedResponse[PaifuSummary] =
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val pageItems = items.slice(query.offset, query.offset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, query.offset, query.offset + pageItems.size < items.size, query.appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  private def toSummary(paifu: Paifu): PaifuSummary =
    PaifuSummary(
      paifuId = paifu.id.value,
      tableId = paifu.metadata.tableId.value,
      tournamentId = paifu.metadata.tournamentId.value,
      stageId = paifu.metadata.stageId.value,
      recordedAt = paifu.metadata.recordedAt.toString,
      source = paifu.metadata.source,
      matchRecordId = paifu.metadata.matchRecordId.map(_.value),
      totalHands = PaifuFunctions.totalHands(paifu),
      playerIds = PaifuFunctions.playerIds(paifu).map(_.value),
      finalStandings = paifu.finalStandings,
      roundScoreChanges = paifu.rounds.map(round =>
        PaifuRoundScoreChanges(
          descriptor = round.descriptor,
          scoreChanges = round.result.scoreChanges
        )
      )
    )

  private final case class ResolvedPaifuListQuery(
      query: PaifuListQuery,
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
