package riichinexus.microservices.tournament.api

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.api.requests.*
import riichinexus.microservices.tournament.objects.TableListQuery
import riichinexus.microservices.tournament.tables.TournamentTables
import riichinexus.microservices.shared.api.requests.OperatorRequest

object TableLifecycleApi:

  def listTables(
      tables: TournamentTables,
      query: TableListQuery
  ): Vector[Table] =
    tables.listTables()
      .filter(table => query.status.forall(_ == table.status))
      .filter(table => query.tournamentId.forall(_ == table.tournamentId))
      .filter(table => query.stageId.forall(_ == table.stageId))
      .filter(table => query.roundNumber.forall(_ == table.stageRoundNumber))
      .filter(table => query.playerId.forall(playerId => table.seats.exists(_.playerId == playerId)))
      .sortBy(table =>
        (table.tournamentId.value, table.stageId.value, table.stageRoundNumber, table.tableNo, table.id.value)
      )

  def findTable(
      tables: TournamentTables,
      tableId: TableId
  ): Option[Table] =
    tables.findTable(tableId)

  def updateSeatState(
      service: TableLifecycleService,
      principalOf: PlayerId => AccessPrincipal,
      tableId: TableId,
      seat: SeatWind,
      request: UpdateTableSeatStateRequest
  ): Option[Table] =
    service.updateSeatState(
      tableId = tableId,
      seat = seat,
      actor = principalOf(request.operator),
      ready = request.ready,
      disconnected = request.disconnected,
      note = request.note
    )

  def updateOwnReadyState(
      service: TableLifecycleService,
      principalOf: PlayerId => AccessPrincipal,
      tableId: TableId,
      request: UpdateOwnTableReadyStateRequest
  ): Option[Table] =
    service.updateOwnReadyState(
      tableId = tableId,
      actor = principalOf(request.operator),
      ready = request.ready,
      note = request.note
    )

  def startTable(
      service: TableLifecycleService,
      principalOf: PlayerId => AccessPrincipal,
      tableId: TableId,
      request: Option[OperatorRequest]
  ): Option[Table] =
    service.startTable(
      tableId,
      actor = request.flatMap(_.operator).map(principalOf).getOrElse(AccessPrincipal.system)
    )

  def recordCompletedTable(
      service: TableLifecycleService,
      principalOf: PlayerId => AccessPrincipal,
      tableId: TableId,
      request: UploadPaifuRequest
  ): Option[Table] =
    service.recordCompletedTable(
      tableId = tableId,
      paifu = request.paifu,
      actor = principalOf(request.operator)
    )

  def forceReset(
      service: TableLifecycleService,
      principalOf: PlayerId => AccessPrincipal,
      tableId: TableId,
      request: ForceResetTableRequest
  ): Option[Table] =
    service.forceReset(
      tableId = tableId,
      note = request.note,
      actor = principalOf(request.operator)
    )
