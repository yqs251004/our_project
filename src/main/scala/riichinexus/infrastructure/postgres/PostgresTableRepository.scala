package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresTableRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TableRepository
    with JdbcRepositorySupport:
  override def save(table: Table): Table =
    val persisted = table.copy(version = table.version + 1)
    val sql =
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

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.tournamentId.value)
        statement.setString(3, persisted.stageId.value)
        statement.setInt(4, persisted.tableNo)
        statement.setString(5, persisted.status.toString)
        statement.setString(6, writeJson[Table](persisted))
        statement.setInt(7, table.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "table",
      persisted.id.value,
      table.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def delete(id: TableId): Unit =
    withConnection { connection =>
      Using.resource(connection.prepareStatement("delete from tables where id = ?")) { statement =>
        statement.setString(1, id.value)
        statement.executeUpdate()
      }
    }

  override def findById(id: TableId): Option[Table] =
    readOne[Table]("select payload from tables where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    readAll[Table](
      "select payload from tables where tournament_id = ? and stage_id = ? order by table_no",
      { statement =>
        statement.setString(1, tournamentId.value)
        statement.setString(2, stageId.value)
      }
    )

  override def findByTournamentIds(tournamentIds: Vector[TournamentId]): Vector[Table] =
    if tournamentIds.isEmpty then Vector.empty
    else
      withConnection { connection =>
        Using.resource(
          connection.prepareStatement(
            """
              |select payload
              |from tables
              |where tournament_id = any(?)
              |order by tournament_id asc, stage_id asc, table_no asc
              |""".stripMargin
          )
        ) { statement =>
          statement.setArray(
            1,
            connection.createArrayOf("text", tournamentIds.map(_.value).toArray)
          )
          Using.resource(statement.executeQuery()) { resultSet =>
            val buffer = Vector.newBuilder[Table]
            while resultSet.next() do
              buffer += read[Table](resultSet.getString("payload"))
            buffer.result()
          }
        }
      }

  override def findAll(): Vector[Table] =
    readAll[Table]("select payload from tables order by updated_at desc")

object PostgresTableRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresTableRepository =
    new PostgresTableRepository(connectionFactory)
