package riichinexus.infrastructure.postgres

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types

import scala.util.Using

import org.postgresql.util.PSQLException

import json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.*

private object PostgresErrors:
  private val UniqueViolation = "23505"

  def isUniqueViolation(error: SQLException, constraint: String): Boolean =
    Option(error.getSQLState).contains(UniqueViolation) &&
      constraintName(error).contains(constraint)

  private def constraintName(error: SQLException): Option[String] =
    error match
      case postgresError: PSQLException =>
        Option(postgresError.getServerErrorMessage).map(_.getConstraint)
      case _ =>
        None

type JdbcConnectionFactory = _root_.database.postgres.JdbcConnectionFactory

type PostgresSchemaInitializer = _root_.database.postgres.PostgresSchemaInitializer

private trait JdbcRepositorySupport:
  protected val connectionFactory: JdbcConnectionFactory

  protected def withConnection[A](f: Connection => A): A =
    connectionFactory.withConnection(f)

  protected def setNullableString(
      statement: PreparedStatement,
      index: Int,
      value: Option[String]
  ): Unit =
    value match
      case Some(actual) => statement.setString(index, actual)
      case None         => statement.setNull(index, Types.VARCHAR)

  protected def readOne[T: Reader](
      sql: String,
      bind: PreparedStatement => Unit
  ): Option[T] =
    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        bind(statement)
        Using.resource(statement.executeQuery()) { resultSet =>
          if resultSet.next() then Some(read[T](resultSet.getString("payload")))
          else None
        }
      }
    }

  protected def readAll[T: Reader](
      sql: String,
      bind: PreparedStatement => Unit = _ => ()
  ): Vector[T] =
    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        bind(statement)
        Using.resource(statement.executeQuery()) { resultSet =>
          val buffer = Vector.newBuilder[T]
          while resultSet.next() do
            buffer += read[T](resultSet.getString("payload"))
          buffer.result()
        }
      }
    }

  protected def writeJson[T: Writer](value: T): String =
    write(value)

  protected def payloadVersionSql(payloadColumn: String = "payload"): String =
    s"cast($payloadColumn ->> 'version' as integer)"

  protected def requireOptimisticUpdate(
      rowsUpdated: Int,
      aggregateType: String,
      aggregateId: String,
      expectedVersion: Int,
      actualVersion: => Option[Int]
  ): Unit =
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        expectedVersion = expectedVersion,
        actualVersion = actualVersion
      )

type PostgresPlayerRepository = _root_.database.postgres.PostgresPlayerRepository

type PostgresGuestSessionRepository = _root_.database.postgres.PostgresGuestSessionRepository

type PostgresClubRepository = _root_.database.postgres.PostgresClubRepository
type PostgresTournamentRepository = _root_.database.postgres.PostgresTournamentRepository
type PostgresTableRepository = _root_.database.postgres.PostgresTableRepository

type PostgresMatchRecordRepository = _root_.database.postgres.PostgresMatchRecordRepository
type PostgresPaifuRepository = _root_.database.postgres.PostgresPaifuRepository

type PostgresAppealTicketRepository = _root_.database.postgres.PostgresAppealTicketRepository
type PostgresDashboardRepository = _root_.database.postgres.PostgresDashboardRepository
type PostgresAdvancedStatsBoardRepository = _root_.database.postgres.PostgresAdvancedStatsBoardRepository
type PostgresAdvancedStatsRecomputeTaskRepository = _root_.database.postgres.PostgresAdvancedStatsRecomputeTaskRepository

type PostgresGlobalDictionaryRepository = _root_.database.postgres.PostgresGlobalDictionaryRepository
type PostgresDictionaryNamespaceRepository = _root_.database.postgres.PostgresDictionaryNamespaceRepository

type PostgresTournamentSettlementRepository = _root_.database.postgres.PostgresTournamentSettlementRepository
type PostgresEventCascadeRecordRepository = _root_.database.postgres.PostgresEventCascadeRecordRepository

type PostgresAuditEventRepository = _root_.database.postgres.PostgresAuditEventRepository

type PostgresDomainEventOutboxRepository = _root_.database.postgres.PostgresDomainEventOutboxRepository
type PostgresDomainEventDeliveryReceiptRepository = _root_.database.postgres.PostgresDomainEventDeliveryReceiptRepository
type PostgresDomainEventSubscriberCursorRepository = _root_.database.postgres.PostgresDomainEventSubscriberCursorRepository

type JdbcTransactionManager = _root_.database.postgres.JdbcTransactionManager
type PostgresAdminService = _root_.database.postgres.PostgresAdminService
