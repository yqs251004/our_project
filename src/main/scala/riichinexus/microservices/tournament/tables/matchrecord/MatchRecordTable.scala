package riichinexus.microservices.tournament.tables.matchrecord

import java.sql.{Connection, ResultSet, Timestamp}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object MatchRecordTable:
  private val upsertSql: String =
    """
      |insert into match_records (id, table_id, tournament_id, stage_id, generated_at, player_ids, payload, updated_at)
      |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
      |on conflict (id) do update set
      |  table_id = excluded.table_id,
      |  tournament_id = excluded.tournament_id,
      |  stage_id = excluded.stage_id,
      |  generated_at = excluded.generated_at,
      |  player_ids = excluded.player_ids,
      |  payload = excluded.payload,
      |  updated_at = now()
      |""".stripMargin

  private[riichinexus] def save(connection: Connection, record: MatchRecord): MatchRecord =
    Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, record.id.value)
      statement.setString(2, record.tableId.value)
      statement.setString(3, record.tournamentId.value)
      statement.setString(4, record.stageId.value)
      statement.setTimestamp(5, Timestamp.from(record.generatedAt))
      statement.setArray(6, connection.createArrayOf("text", record.playerIds.map(_.value).toArray))
      statement.setString(7, write[MatchRecord](record))
      statement.executeUpdate()
    }
    record

  private val findByIdSql: String =
    """
      |select payload
      |from match_records
      |where id = ?
      |""".stripMargin

  private[riichinexus] def findById(connection: Connection, id: MatchRecordId): Option[MatchRecord] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readMatchRecord(resultSet))
        else None
      }
    }

  private val findByTableSql: String =
    """
      |select payload
      |from match_records
      |where table_id = ?
      |""".stripMargin

  private[riichinexus] def findByTable(connection: Connection, tableId: TableId): Option[MatchRecord] =
    Using.resource(connection.prepareStatement(findByTableSql)) { statement =>
      statement.setString(1, tableId.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readMatchRecord(resultSet))
        else None
      }
    }

  private val findByTournamentAndStageSql: String =
    """
      |select payload
      |from match_records
      |where tournament_id = ? and stage_id = ?
      |order by generated_at desc, id desc
      |""".stripMargin

  private[riichinexus] def findByTournamentAndStage(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    Using.resource(connection.prepareStatement(findByTournamentAndStageSql)) { statement =>
      statement.setString(1, tournamentId.value)
      statement.setString(2, stageId.value)
      Using.resource(statement.executeQuery())(readMatchRecords)
    }

  private val findRecentByClubSql: String =
    """
      |select payload
      |from match_records
      |where exists (
      |  select 1
      |  from jsonb_array_elements(payload -> 'seatResults') as seat
      |  where seat ->> 'clubId' = ?
      |)
      |order by generated_at desc, id desc
      |limit ?
      |""".stripMargin

  private[riichinexus] def findRecentByClub(connection: Connection, clubId: ClubId, limit: Int): Vector[MatchRecord] =
    Using.resource(connection.prepareStatement(findRecentByClubSql)) { statement =>
      statement.setString(1, clubId.value)
      statement.setInt(2, limit)
      Using.resource(statement.executeQuery())(readMatchRecords)
    }

  private val findAllSql: String =
    """
      |select payload
      |from match_records
      |order by generated_at desc
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[MatchRecord] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readMatchRecords)
    }

  private def readMatchRecords(resultSet: ResultSet): Vector[MatchRecord] =
    @tailrec
    def loop(acc: Vector[MatchRecord]): Vector[MatchRecord] =
      if resultSet.next() then loop(readMatchRecord(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readMatchRecord(resultSet: ResultSet): MatchRecord =
    read[MatchRecord](resultSet.getString("payload"))
