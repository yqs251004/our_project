package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresPaifuRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends PaifuRepository
    with JdbcRepositorySupport:
  override def save(paifu: Paifu): Paifu =
    val sql =
      """
        |insert into paifus (id, table_id, tournament_id, stage_id, recorded_at, player_ids, payload, updated_at)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  table_id = excluded.table_id,
        |  tournament_id = excluded.tournament_id,
        |  stage_id = excluded.stage_id,
        |  recorded_at = excluded.recorded_at,
        |  player_ids = excluded.player_ids,
        |  payload = excluded.payload,
        |  updated_at = now()
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, paifu.id.value)
        statement.setString(2, paifu.metadata.tableId.value)
        statement.setString(3, paifu.metadata.tournamentId.value)
        statement.setString(4, paifu.metadata.stageId.value)
        statement.setTimestamp(5, Timestamp.from(paifu.metadata.recordedAt))
        statement.setArray(
          6,
          connection.createArrayOf("text", paifu.playerIds.map(_.value).toArray)
        )
        statement.setString(7, writeJson[Paifu](paifu))
        statement.executeUpdate()
      }
    }

    paifu

  override def findById(id: PaifuId): Option[Paifu] =
    readOne[Paifu]("select payload from paifus where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findAll(): Vector[Paifu] =
    readAll[Paifu]("select payload from paifus order by recorded_at desc")

  override def findByPlayer(playerId: PlayerId): Vector[Paifu] =
    readAll[Paifu](
      "select payload from paifus where ? = any(player_ids) order by recorded_at desc",
      { statement =>
        statement.setString(1, playerId.value)
      }
    )

object PostgresPaifuRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresPaifuRepository =
    new PostgresPaifuRepository(connectionFactory)
