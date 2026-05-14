package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresClubRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends ClubRepository
    with JdbcRepositorySupport:
  override def save(club: Club): Club =
    try persist(club)
    catch
      case error: SQLException if PostgresErrors.isUniqueViolation(error, "idx_clubs_name") =>
        val normalized = findByName(club.name)
          .map(existing =>
            club.copy(
              id = existing.id,
              creator = existing.creator,
              createdAt = existing.createdAt,
              version = existing.version
            )
          )
          .getOrElse(throw error)
        persist(normalized)

  private def persist(club: Club): Club =
    val persisted = club.copy(version = club.version + 1)
    val sql =
      """
        |insert into clubs (id, name, creator_id, total_points, payload, updated_at)
        |values (?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  name = excluded.name,
        |  creator_id = excluded.creator_id,
        |  total_points = excluded.total_points,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(clubs.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.name)
        statement.setString(3, persisted.creator.value)
        statement.setInt(4, persisted.totalPoints)
        statement.setString(5, writeJson[Club](persisted))
        statement.setInt(6, club.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "club",
      persisted.id.value,
      club.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: ClubId): Option[Club] =
    readOne[Club]("select payload from clubs where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByName(name: String): Option[Club] =
    readOne[Club]("select payload from clubs where name = ?", { statement =>
      statement.setString(1, name)
    })

  override def findByIds(ids: Vector[ClubId]): Vector[Club] =
    if ids.isEmpty then Vector.empty
    else
      withConnection { connection =>
        Using.resource(
          connection.prepareStatement(
            """
              |select payload
              |from clubs
              |where id = any(?)
              |order by name asc
              |""".stripMargin
          )
        ) { statement =>
          statement.setArray(
            1,
            connection.createArrayOf("text", ids.map(_.value).distinct.toArray)
          )
          Using.resource(statement.executeQuery()) { resultSet =>
            val buffer = Vector.newBuilder[Club]
            while resultSet.next() do
              buffer += read[Club](resultSet.getString("payload"))
            buffer.result()
          }
        }
      }

  override def findFiltered(
      activeOnly: Boolean = false,
      joinableOnly: Boolean = false,
      memberId: Option[PlayerId] = None,
      adminId: Option[PlayerId] = None,
      name: Option[String] = None
  ): Vector[Club] =
    readAll[Club](
      """
        |select payload
        |from clubs
        |where (? = false or payload ->> 'dissolvedAt' is null)
        |  and (? = false or (
        |    payload ->> 'dissolvedAt' is null and
        |    coalesce((payload #>> '{recruitmentPolicy,applicationsOpen}')::boolean, false)
        |  ))
        |  and (? is null or payload @> cast(? as jsonb))
        |  and (? is null or payload @> cast(? as jsonb))
        |  and (? is null or lower(name) like ?)
        |order by name asc
        |""".stripMargin,
      { statement =>
        statement.setBoolean(1, activeOnly)
        statement.setBoolean(2, joinableOnly)
        setNullableString(statement, 3, memberId.map(_.value))
        setNullableString(statement, 4, memberId.map(id => s"""{"members":[{"value":"${id.value}"}]}"""))
        setNullableString(statement, 5, adminId.map(_.value))
        setNullableString(statement, 6, adminId.map(id => s"""{"admins":[{"value":"${id.value}"}]}"""))
        setNullableString(statement, 7, name)
        setNullableString(statement, 8, name.map(fragment => s"%${fragment.toLowerCase}%"))
      }
    )

  override def findAll(): Vector[Club] =
    readAll[Club]("select payload from clubs order by name")

object PostgresClubRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresClubRepository =
    new PostgresClubRepository(connectionFactory)
