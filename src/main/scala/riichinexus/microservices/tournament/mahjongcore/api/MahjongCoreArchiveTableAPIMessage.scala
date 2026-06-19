package riichinexus.microservices.tournament.mahjongcore.api

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.paifumanagement.functions.MahjongTableArchiveFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes.MahjongActionResponse
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.ArchiveMahjongTableRequest
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 将已完成的实时麻将桌归档为 Paifu 和 MatchRecord。 */
final case class MahjongCoreArchiveTableAPIMessage(
    tableId: String,
    request: ArchiveMahjongTableRequest
) extends APIMessage[MahjongActionResponse]:

  override def plan(context: ApiPlanContext): IO[MahjongActionResponse] =
    for
      command <- IO.delay(ArchiveMahjongTableCommand(TableId(tableId)))
      archivedAt <- IO.realTimeInstant
      archived <- IO.blocking(archiveTable(context, command, archivedAt))
      storedState <- IO.blocking(saveArchivedState(context, archived))
    yield toResponse(storedState, archived)

  private def archiveTable(
      context: ApiPlanContext,
      command: ArchiveMahjongTableCommand,
      archivedAt: Instant
  ) =
    val current = MahjongTableStateTable
      .findById(context.connection, command.tableId)
      .getOrElse(throw IllegalArgumentException(s"Mahjong table ${command.tableId.value} is not started"))
    MahjongTableArchiveFunctions.archive(context.connection, current, archivedAt)

  private def saveArchivedState(
      context: ApiPlanContext,
      archived: MahjongTableArchiveFunctions.ArchivedMahjongTable
  ) =
    MahjongTableStateTable.save(
      context.connection,
      archived.tableState.copy(version = archived.tableState.version + 1),
      archivedPaifuId = Some(archived.paifu.id),
      archivedMatchRecordId = Some(archived.matchRecord.id)
    )

  private def toResponse(
      storedState: riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.MahjongTableState,
      archived: MahjongTableArchiveFunctions.ArchivedMahjongTable
  ): MahjongActionResponse =
    MahjongActionResponse(
        table = MahjongGameStateTransitionFunctions.toView(storedState, viewerPlayerId = None, includeLegalActions = false),
        acceptedEvent = None,
        archivedPaifuId = Some(archived.paifu.id)
      )

  private final case class ArchiveMahjongTableCommand(tableId: TableId)
