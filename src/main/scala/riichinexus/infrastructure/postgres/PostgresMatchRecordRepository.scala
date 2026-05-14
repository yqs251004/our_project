package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresMatchRecordRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends MatchRecordRepository
    with JdbcRepositorySupport:
  override def save(record: MatchRecord): MatchRecord =
    val sql =
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

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, record.id.value)
        statement.setString(2, record.tableId.value)
        statement.setString(3, record.tournamentId.value)
        statement.setString(4, record.stageId.value)
        statement.setTimestamp(5, Timestamp.from(record.generatedAt))
        statement.setArray(
          6,
          connection.createArrayOf("text", record.playerIds.map(_.value).toArray)
        )
        statement.setString(7, writeJson[MatchRecord](record))
        statement.executeUpdate()
      }
    }

    record

  override def findById(id: MatchRecordId): Option[MatchRecord] =
    readOne[MatchRecord]("select payload from match_records where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByTable(tableId: TableId): Option[MatchRecord] =
    readOne[MatchRecord]("select payload from match_records where table_id = ?", { statement =>
      statement.setString(1, tableId.value)
    })

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    readAll[MatchRecord](
      """
        |select payload
        |from match_records
        |where tournament_id = ? and stage_id = ?
        |order by generated_at desc, id desc
        |""".stripMargin,
      { statement =>
        statement.setString(1, tournamentId.value)
        statement.setString(2, stageId.value)
      }
    )

  override def findRecentByClub(clubId: ClubId, limit: Int): Vector[MatchRecord] =
    readAll[MatchRecord](
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
        |""".stripMargin,
      { statement =>
        statement.setString(1, clubId.value)
        statement.setInt(2, limit)
      }
    )

  override def findAll(): Vector[MatchRecord] =
    readAll[MatchRecord]("select payload from match_records order by generated_at desc")

object PostgresMatchRecordRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresMatchRecordRepository =
    new PostgresMatchRecordRepository(connectionFactory)
