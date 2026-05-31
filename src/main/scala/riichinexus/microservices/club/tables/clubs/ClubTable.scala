package riichinexus.microservices.club.tables.clubs

import java.sql.{Connection, PreparedStatement, ResultSet, Types}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object ClubTable:
  private val upsertSql: String =
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

  private[club] def save(connection: Connection, club: Club): Club =
    val persisted = club.copy(version = club.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setString(2, persisted.name)
      statement.setString(3, persisted.creator.value)
      statement.setInt(4, persisted.totalPoints)
      statement.setString(5, write[Club](persisted))
      statement.setInt(6, club.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "club",
        aggregateId = persisted.id.value,
        expectedVersion = club.version,
        actualVersion = findById(connection, persisted.id).map(_.version)
      )
    persisted

  private val findByIdSql: String =
    """
      |select payload
      |from clubs
      |where id = ?
      |""".stripMargin

  private[club] def findById(connection: Connection, id: ClubId): Option[Club] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readClub(resultSet))
        else None
      }
    }

  private val findByNameSql: String =
    """
      |select payload
      |from clubs
      |where name = ?
      |""".stripMargin

  private[club] def findByName(connection: Connection, name: String): Option[Club] =
    Using.resource(connection.prepareStatement(findByNameSql)) { statement =>
      statement.setString(1, name)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readClub(resultSet))
        else None
      }
    }

  private val findByIdsSql: String =
    """
      |select payload
      |from clubs
      |where id = any(?)
      |order by name asc
      |""".stripMargin

  private[club] def findByIds(connection: Connection, ids: Vector[ClubId]): Vector[Club] =
    if ids.isEmpty then Vector.empty
    else
      Using.resource(connection.prepareStatement(findByIdsSql)) { statement =>
        statement.setArray(
          1,
          connection.createArrayOf("text", ids.map(_.value).distinct.toArray)
        )
        Using.resource(statement.executeQuery())(readClubs)
      }

  private val findFilteredSql: String =
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
      |""".stripMargin

  private[club] def findFiltered(
      connection: Connection,
      activeOnly: Boolean = false,
      joinableOnly: Boolean = false,
      memberId: Option[PlayerId] = None,
      adminId: Option[PlayerId] = None,
      name: Option[String] = None
  ): Vector[Club] =
    Using.resource(connection.prepareStatement(findFilteredSql)) { statement =>
      statement.setBoolean(1, activeOnly)
      statement.setBoolean(2, joinableOnly)
      setNullableString(statement, 3, memberId.map(_.value))
      setNullableString(statement, 4, memberId.map(id => s"""{"members":[{"value":"${id.value}"}]}"""))
      setNullableString(statement, 5, adminId.map(_.value))
      setNullableString(statement, 6, adminId.map(id => s"""{"admins":[{"value":"${id.value}"}]}"""))
      setNullableString(statement, 7, name)
      setNullableString(statement, 8, name.map(fragment => s"%${fragment.toLowerCase}%"))
      Using.resource(statement.executeQuery())(readClubs)
    }

  private val findAllSql: String =
    """
      |select payload
      |from clubs
      |order by name
      |""".stripMargin

  private[club] def findAll(connection: Connection): Vector[Club] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readClubs)
    }

  private def setNullableString(
      statement: PreparedStatement,
      index: Int,
      value: Option[String]
  ): Unit =
    value match
      case Some(actual) => statement.setString(index, actual)
      case None         => statement.setNull(index, Types.VARCHAR)

  private def readClubs(resultSet: ResultSet): Vector[Club] =
    @tailrec
    def loop(acc: Vector[Club]): Vector[Club] =
      if resultSet.next() then loop(readClub(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readClub(resultSet: ResultSet): Club =
    read[Club](resultSet.getString("payload"))
