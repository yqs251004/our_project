package riichinexus.microservices.tournament.mahjongcore.api

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.paifumanagement.functions.MahjongTableArchiveFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes.MahjongActionResponse
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.ArchiveMahjongTableRequest
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 将已完成的实时麻将桌归档为 Paifu 和 MatchRecord。 */
final case class MahjongCoreArchiveTableAPIMessage(
    tableId: String,
    request: ArchiveMahjongTableRequest
) extends APIMessage[MahjongActionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongActionResponse] =
    val archivedAt = Instant.now()
    for
      archived <- IO.blocking {
        val id = TableId(tableId)
        val current = MahjongTableStateTable.findById(context.connection, id)
          .getOrElse(throw IllegalArgumentException(s"Mahjong table ${tableId} is not started"))
        MahjongTableArchiveFunctions.archive(context.connection, current, archivedAt)
      }
      storedState <- IO.blocking {
        MahjongTableStateTable.save(
          context.connection,
          archived.tableState.copy(version = archived.tableState.version + 1),
          archivedPaifuId = Some(archived.paifu.id),
          archivedMatchRecordId = Some(archived.matchRecord.id)
        )
      }
    yield MahjongActionResponse(
        table = MahjongGameStateTransitionFunctions.toView(storedState, viewerPlayerId = None, includeLegalActions = false),
        acceptedEvent = None,
        archivedPaifuId = Some(archived.paifu.id)
      )
