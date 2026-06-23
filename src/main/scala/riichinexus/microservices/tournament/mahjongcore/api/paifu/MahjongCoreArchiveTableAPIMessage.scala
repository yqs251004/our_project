package riichinexus.microservices.tournament.mahjongcore.api.paifu
import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.paifumanagement.functions.MahjongTableArchiveFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.paifumanagement.model.ArchivedMahjongTable
import riichinexus.microservices.tournament.mahjongcore.domain.realtime.functions.MahjongRealtimeEventFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes.MahjongActionResponse
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.ArchiveMahjongTableRequest
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.realtime.objects.RealtimeSourceEventType

/** 将已完成的实时麻将桌归档为 Paifu 和 MatchRecord。 */
final case class MahjongCoreArchiveTableAPIMessage(
    tableId: String,
    request: ArchiveMahjongTableRequest
) extends APIMessage[MahjongActionResponse]:

  override def plan(context: ApiPlanContext): IO[MahjongActionResponse] =
    for
      requestedTableId <- IO.delay(TableId(tableId))
      archivedAt <- IO.realTimeInstant
      archived <- IO.blocking(archiveTable(context, requestedTableId, archivedAt))
      storedState <- IO.blocking(saveArchivedState(context, archived))
      response = toResponse(storedState, archived)
      _ <- context.afterCommit(
        context.realtimeEventBus.publish(
          MahjongRealtimeEventFunctions.tableChanged(
            tableId = requestedTableId,
            sourceEventType = RealtimeSourceEventType.MahjongTableArchived,
            table = response.table,
            actorId = request.operatorId.filter(_.nonEmpty).map(PlayerId(_)),
            occurredAt = archivedAt
          )
        )
      )
    yield response

  private def archiveTable(
      context: ApiPlanContext,
      tableId: TableId,
      archivedAt: Instant
  ) =
    val current = MahjongTableStateTable
      .findById(context.connection, tableId)
      .getOrElse(throw IllegalArgumentException(s"Mahjong table ${tableId.value} is not started"))
    MahjongTableArchiveFunctions.archive(context.connection, current, archivedAt)

  private def saveArchivedState(
      context: ApiPlanContext,
      archived: ArchivedMahjongTable
  ) =
    MahjongTableStateTable.save(
      context.connection,
      archived.tableState.copy(version = archived.tableState.version + 1),
      archivedPaifuId = Some(archived.paifu.id),
      archivedMatchRecordId = Some(archived.matchRecord.id)
    )

  private def toResponse(
      storedState: riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.MahjongTableState,
      archived: ArchivedMahjongTable
  ): MahjongActionResponse =
    MahjongActionResponse(
        table = MahjongGameStateTransitionFunctions.toView(storedState, viewerPlayerId = None, includeLegalActions = false),
        acceptedEvent = None,
        archivedPaifuId = Some(archived.paifu.id)
      )

