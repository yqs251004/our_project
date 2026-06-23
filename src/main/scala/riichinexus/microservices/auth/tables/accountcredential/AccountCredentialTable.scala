package riichinexus.microservices.auth.tables.accountcredential

import riichinexus.system.objects.`private`.AggregateType

import java.sql.{Connection, ResultSet, SQLException}

import scala.annotation.tailrec
import scala.util.Using

import org.postgresql.util.PSQLException
import riichinexus.system.errors.OptimisticConcurrencyException
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.domain.account.functions.AccountCredentialFunctions
import riichinexus.microservices.auth.domain.account.model.AccountCredential
import upickle.default.{read, write}


object AccountCredentialTable:
  private val upsertSql: String =
    """
      |insert into account_credentials (username, player_id, payload, updated_at)
      |values (?, ?, cast(? as jsonb), now())
      |on conflict (username) do update set
      |  player_id = excluded.player_id,
      |  payload = excluded.payload,
      |  updated_at = now()
      |where cast(account_credentials.payload ->> 'version' as integer) = ?
      |""".stripMargin

  private[auth] def save(connection: Connection, credential: AccountCredential): AccountCredential =
    AccountCredentialFunctions.validate(credential)

    def persist(candidate: AccountCredential): AccountCredential =
      val persisted = candidate.copy(version = candidate.version + 1)
      val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
        statement.setString(1, persisted.username)
        statement.setString(2, persisted.playerId.value)
        statement.setString(3, write[AccountCredential](persisted))
        statement.setInt(4, candidate.version)
        statement.executeUpdate()
      }
      if rowsUpdated == 0 then
        throw OptimisticConcurrencyException(
          aggregateType = AggregateType.AccountCredential,
          aggregateId = persisted.username,
          expectedVersion = candidate.version,
          actualVersion = findByUsername(connection, persisted.username).map(_.version)
        )
      persisted

    try persist(credential)
    catch
      case error: SQLException if isUniqueViolation(error, "idx_account_credentials_player_id") =>
        throw IllegalArgumentException(s"Player ${credential.playerId.value} already has a registered account")

  private val findByUsernameSql: String =
    """
      |select payload
      |from account_credentials
      |where username = ?
      |""".stripMargin

  private[auth] def findByUsername(connection: Connection, username: String): Option[AccountCredential] =
    Using.resource(connection.prepareStatement(findByUsernameSql)) { statement =>
      statement.setString(1, AccountCredentialFunctions.normalizeUsername(username))
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readCredential(resultSet))
        else None
      }
    }

  private val findByPlayerIdSql: String =
    """
      |select payload
      |from account_credentials
      |where player_id = ?
      |""".stripMargin

  private[auth] def findByPlayerId(connection: Connection, playerId: PlayerId): Option[AccountCredential] =
    Using.resource(connection.prepareStatement(findByPlayerIdSql)) { statement =>
      statement.setString(1, playerId.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readCredential(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from account_credentials
      |order by username
      |""".stripMargin

  private[auth] def findAll(connection: Connection): Vector[AccountCredential] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readCredentials)
    }

  private def readCredentials(resultSet: ResultSet): Vector[AccountCredential] =
    @tailrec
    def loop(acc: Vector[AccountCredential]): Vector[AccountCredential] =
      if resultSet.next() then loop(readCredential(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readCredential(resultSet: ResultSet): AccountCredential =
    read[AccountCredential](resultSet.getString("payload"))

  private def isUniqueViolation(error: SQLException, constraintName: String): Boolean =
    error match
      case postgresError: PSQLException =>
        Option(postgresError.getServerErrorMessage).exists(_.getConstraint == constraintName)
      case _ => false
