package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresPlayerRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends PlayerRepository
    with JdbcRepositorySupport:
  override def save(player: Player): Player =
    try persist(player)
    catch
      case error: SQLException if PostgresErrors.isUniqueViolation(error, "idx_players_user_id") =>
        val normalized = findByUserId(player.userId)
          .map(existing =>
            player.copy(
              id = existing.id,
              registeredAt = existing.registeredAt,
              version = existing.version
            )
          )
          .getOrElse(throw error)
        persist(normalized)

  private def persist(player: Player): Player =
    val persisted = player.copy(version = player.version + 1)
    val sql =
      """
        |insert into players (id, user_id, nickname, club_id, elo, payload, updated_at)
        |values (?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  user_id = excluded.user_id,
        |  nickname = excluded.nickname,
        |  club_id = excluded.club_id,
        |  elo = excluded.elo,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(players.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.userId)
        statement.setString(3, persisted.nickname)
        setNullableString(statement, 4, persisted.clubId.map(_.value))
        statement.setInt(5, persisted.elo)
        statement.setString(6, writeJson[Player](persisted))
        statement.setInt(7, player.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "player",
      persisted.id.value,
      player.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: PlayerId): Option[Player] =
    readOne[Player]("select payload from players where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByUserId(userId: String): Option[Player] =
    readOne[Player]("select payload from players where user_id = ?", { statement =>
      statement.setString(1, userId)
    })

  override def findByIds(ids: Vector[PlayerId]): Vector[Player] =
    if ids.isEmpty then Vector.empty
    else
      withConnection { connection =>
        Using.resource(
          connection.prepareStatement(
            """
              |select payload
              |from players
              |where id = any(?)
              |order by nickname asc
              |""".stripMargin
          )
        ) { statement =>
          statement.setArray(
            1,
            connection.createArrayOf("text", ids.map(_.value).distinct.toArray)
          )
          Using.resource(statement.executeQuery()) { resultSet =>
            val buffer = Vector.newBuilder[Player]
            while resultSet.next() do
              buffer += read[Player](resultSet.getString("payload"))
            buffer.result()
          }
        }
      }

  override def findByClub(clubId: ClubId): Vector[Player] =
    readAll[Player](
      "select payload from players where club_id = ? order by nickname",
      { statement =>
        statement.setString(1, clubId.value)
      }
    )

  override def findAll(): Vector[Player] =
    readAll[Player]("select payload from players order by nickname")

object PostgresPlayerRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresPlayerRepository =
    new PostgresPlayerRepository(connectionFactory)
