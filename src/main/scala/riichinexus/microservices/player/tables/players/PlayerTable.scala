package riichinexus.microservices.player.tables.players

import java.sql.{Connection, ResultSet, SQLException, Types}

import scala.annotation.tailrec
import scala.util.Using

import org.postgresql.util.PSQLException
import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerClubBindingFunctions
import riichinexus.microservices.player.objects.apiTypes.PlayerListQuery
import upickle.default.{read, write}

object PlayerTable:
  val OwnedTables: Vector[String] = Vector(
    "players"
  )

  private val upsertSql: String =
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

  private[player] def save(connection: Connection, player: Player): Player =
    def persist(candidate: Player): Player =
      val persisted = candidate.copy(version = candidate.version + 1)
      val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.userId)
        statement.setString(3, persisted.nickname)
        persisted.clubId match
          case Some(clubId) => statement.setString(4, clubId.value)
          case None         => statement.setNull(4, Types.VARCHAR)
        statement.setInt(5, persisted.elo)
        statement.setString(6, write[Player](persisted))
        statement.setInt(7, candidate.version)
        statement.executeUpdate()
      }
      if rowsUpdated == 0 then
        throw OptimisticConcurrencyException(
          aggregateType = "player",
          aggregateId = persisted.id.value,
          expectedVersion = candidate.version,
          actualVersion = findById(connection, persisted.id).map(_.version)
        )
      persisted

    try persist(player)
    catch
      case error: SQLException if isUniqueViolation(error, "idx_players_user_id") =>
        val normalized = findByUserId(connection, player.userId)
          .map(existing =>
            player.copy(
              id = existing.id,
              registeredAt = existing.registeredAt,
              version = existing.version
            )
          )
          .getOrElse(throw error)
        persist(normalized)

  private val findByIdSql: String =
    """
      |select payload
      |from players
      |where id = ?
      |""".stripMargin

  private[player] def findById(connection: Connection, id: PlayerId): Option[Player] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readPlayer(resultSet))
        else None
      }
    }

  private val findByUserIdSql: String =
    """
      |select payload
      |from players
      |where user_id = ?
      |""".stripMargin

  private[player] def findByUserId(connection: Connection, userId: String): Option[Player] =
    Using.resource(connection.prepareStatement(findByUserIdSql)) { statement =>
      statement.setString(1, userId)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readPlayer(resultSet))
        else None
      }
    }

  private val listSql: String =
    """
      |select payload
      |from players
      |where (? is null or club_id = ?)
      |order by nickname
      |""".stripMargin

  private[player] def list(connection: Connection, query: PlayerListQuery): Vector[Player] =
    Using.resource(connection.prepareStatement(listSql)) { statement =>
      query.clubId match
        case Some(clubId) =>
          statement.setString(1, clubId.value)
          statement.setString(2, clubId.value)
        case None =>
          statement.setNull(1, Types.VARCHAR)
          statement.setNull(2, Types.VARCHAR)
      Using.resource(statement.executeQuery())(readPlayers)
    }
      .filter(player => query.clubId.forall(PlayerClubBindingFunctions.boundClubIds(player).contains))
      .filter(player => query.status.forall(_ == player.status))
      .sortBy(player => (player.nickname, player.id.value))

  private val findByIdsSql: String =
    """
      |select payload
      |from players
      |where id = any(?)
      |order by nickname asc
      |""".stripMargin

  private[player] def findByIds(connection: Connection, ids: Vector[PlayerId]): Vector[Player] =
    if ids.isEmpty then Vector.empty
    else
      Using.resource(connection.prepareStatement(findByIdsSql)) { statement =>
        statement.setArray(
          1,
          connection.createArrayOf("text", ids.map(_.value).distinct.toArray)
        )
        Using.resource(statement.executeQuery())(readPlayers)
      }

  private val findByClubSql: String =
    """
      |select payload
      |from players
      |where club_id = ?
      |order by nickname
      |""".stripMargin

  private[player] def findByClub(connection: Connection, clubId: ClubId): Vector[Player] =
    Using.resource(connection.prepareStatement(findByClubSql)) { statement =>
      statement.setString(1, clubId.value)
      Using.resource(statement.executeQuery())(readPlayers)
    }

  private val findAllSql: String =
    """
      |select payload
      |from players
      |order by nickname
      |""".stripMargin

  private[player] def findAll(connection: Connection): Vector[Player] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readPlayers)
    }

  private def readPlayers(resultSet: ResultSet): Vector[Player] =
    @tailrec
    def loop(acc: Vector[Player]): Vector[Player] =
      if resultSet.next() then loop(readPlayer(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readPlayer(resultSet: ResultSet): Player =
    read[Player](resultSet.getString("payload"))

  private def isUniqueViolation(error: SQLException, constraintName: String): Boolean =
    error match
      case postgresError: PSQLException =>
        Option(postgresError.getServerErrorMessage).exists(_.getConstraint == constraintName)
      case _ => false
