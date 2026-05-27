package riichinexus.microservices.tournament.tables.tournamentgame

import java.sql.{Connection, ResultSet}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object TournamentGameTable:
  private val upsertSql: String =
    """
      |insert into tables (id, tournament_id, stage_id, table_no, status, payload, updated_at)
      |values (?, ?, ?, ?, ?, cast(? as jsonb), now())
      |on conflict (id) do update set
      |  tournament_id = excluded.tournament_id,
      |  stage_id = excluded.stage_id,
      |  table_no = excluded.table_no,
      |  status = excluded.status,
      |  payload = excluded.payload,
      |  updated_at = now()
      |where cast(tables.payload ->> 'version' as integer) = ?
      |""".stripMargin

  private[riichinexus] def save(connection: Connection, table: Table): Table =
    val persisted = table.copy(version = table.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setString(2, persisted.tournamentId.value)
      statement.setString(3, persisted.stageId.value)
      statement.setInt(4, persisted.tableNo)
      statement.setString(5, persisted.status.toString)
      statement.setString(6, write[Table](persisted))
      statement.setInt(7, table.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "table",
        aggregateId = persisted.id.value,
        expectedVersion = table.version,
        actualVersion = findById(connection, persisted.id).map(_.version)
      )
    persisted

  private val deleteSql: String =
    """
      |delete from tables
      |where id = ?
      |""".stripMargin

  private[riichinexus] def delete(connection: Connection, id: TableId): Unit =
    Using.resource(connection.prepareStatement(deleteSql)) { statement =>
      statement.setString(1, id.value)
      statement.executeUpdate()
      ()
    }

  private val findByIdSql: String =
    """
      |select payload
      |from tables
      |where id = ?
      |""".stripMargin

  private[riichinexus] def findById(connection: Connection, id: TableId): Option[Table] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readTable(resultSet))
        else None
      }
    }

  private val findByTournamentAndStageSql: String =
    """
      |select payload
      |from tables
      |where tournament_id = ? and stage_id = ?
      |order by table_no
      |""".stripMargin

  private[riichinexus] def findByTournamentAndStage(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    Using.resource(connection.prepareStatement(findByTournamentAndStageSql)) { statement =>
      statement.setString(1, tournamentId.value)
      statement.setString(2, stageId.value)
      Using.resource(statement.executeQuery())(readTables)
    }

  private val findByTournamentIdsSql: String =
    """
      |select payload
      |from tables
      |where tournament_id = any(?)
      |order by tournament_id asc, stage_id asc, table_no asc
      |""".stripMargin

  private[riichinexus] def findByTournamentIds(connection: Connection, tournamentIds: Vector[TournamentId]): Vector[Table] =
    if tournamentIds.isEmpty then Vector.empty
    else
      Using.resource(connection.prepareStatement(findByTournamentIdsSql)) { statement =>
        statement.setArray(
          1,
          connection.createArrayOf("text", tournamentIds.map(_.value).toArray)
        )
        Using.resource(statement.executeQuery())(readTables)
      }

  private val findAllSql: String =
    """
      |select payload
      |from tables
      |order by updated_at desc
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[Table] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readTables)
    }

  private def readTables(resultSet: ResultSet): Vector[Table] =
    @tailrec
    def loop(acc: Vector[Table]): Vector[Table] =
      if resultSet.next() then loop(readTable(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readTable(resultSet: ResultSet): Table =
    read[Table](resultSet.getString("payload"))
