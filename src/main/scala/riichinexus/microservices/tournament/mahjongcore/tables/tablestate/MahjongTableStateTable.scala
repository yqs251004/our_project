package riichinexus.microservices.tournament.mahjongcore.tables.tablestate

import java.sql.{Connection, ResultSet}

import scala.util.Using

import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.MahjongTableState
import riichinexus.system.json.MahjongTableStateJsonCodecs.given
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import upickle.default.{read, write}

object MahjongTableStateTable:
  private val upsertSql: String =
    """
      |insert into mahjong_table_states (table_id, status, version, payload, archived_paifu_id, archived_match_record_id, updated_at)
      |values (?, ?, ?, cast(? as jsonb), ?, ?, now())
      |on conflict (table_id) do update set
      |  status = excluded.status,
      |  version = excluded.version,
      |  payload = excluded.payload,
      |  archived_paifu_id = excluded.archived_paifu_id,
      |  archived_match_record_id = excluded.archived_match_record_id,
      |  updated_at = now()
      |""".stripMargin

  private[mahjongcore] def save(
      connection: Connection,
      state: MahjongTableState,
      archivedPaifuId: Option[PaifuId] = None,
      archivedMatchRecordId: Option[MatchRecordId] = None
  ): MahjongTableState =
    Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, state.tableId.value)
      statement.setString(2, state.status.toString)
      statement.setInt(3, state.version)
      statement.setString(4, write[MahjongTableState](state))
      statement.setString(5, archivedPaifuId.map(_.value).orNull)
      statement.setString(6, archivedMatchRecordId.map(_.value).orNull)
      statement.executeUpdate()
    }
    state

  private val findByIdSql: String =
    """
      |select payload
      |from mahjong_table_states
      |where table_id = ?
      |""".stripMargin

  private[mahjongcore] def findById(connection: Connection, tableId: TableId): Option[MahjongTableState] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, tableId.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readState(resultSet)) else None
      }
    }

  private val deleteSql: String =
    """
      |delete from mahjong_table_states
      |where table_id = ?
      |""".stripMargin

  private[mahjongcore] def delete(connection: Connection, tableId: TableId): Unit =
    Using.resource(connection.prepareStatement(deleteSql)) { statement =>
      statement.setString(1, tableId.value)
      statement.executeUpdate()
      ()
    }

  private def readState(resultSet: ResultSet): MahjongTableState =
    read[MahjongTableState](resultSet.getString("payload"))
