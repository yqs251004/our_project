package database.postgres

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types

import scala.util.Using

import ports.OptimisticConcurrencyException
import upickle.default.*

trait JdbcRepositorySupport:
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
