package riichinexus.microservices.tournament.api

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.api.requests.*
import riichinexus.microservices.tournament.objects.TournamentSettlementQuery
import riichinexus.microservices.tournament.tables.TournamentTables

object TournamentSettlementApi:

  def listSettlements(
      tables: TournamentTables,
      tournamentId: TournamentId,
      query: TournamentSettlementQuery
  ): Vector[TournamentSettlementSnapshot] =
    tables.listSettlements(tournamentId, query)

  def findSettlement(
      tables: TournamentTables,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot] =
    tables.findSettlement(tournamentId, stageId)

  def settleTournament(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      request: SettleTournamentRequest
  ): TournamentSettlementSnapshot =
    service.settleTournament(
      tournamentId = tournamentId,
      finalStageId = request.stageId,
      prizePool = request.prizePool,
      payoutRatios = request.payoutRatios,
      houseFeeAmount = request.houseFeeAmount,
      clubShareRatio = request.clubShareRatio,
      adjustments = request.adjustments.map(_.adjustment),
      finalize = request.finalizeSettlement,
      note = request.note,
      actor = principalOf(request.operator)
    )

  def finalizeSettlement(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      settlementId: SettlementSnapshotId,
      request: FinalizeTournamentSettlementRequest
  ): Option[TournamentSettlementSnapshot] =
    service.finalizeTournamentSettlement(
      tournamentId = tournamentId,
      settlementId = settlementId,
      actor = principalOf(request.operator),
      note = request.note
    )
