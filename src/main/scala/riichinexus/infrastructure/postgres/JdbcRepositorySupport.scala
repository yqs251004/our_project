package riichinexus.infrastructure.postgres

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
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
          readPayloads(resultSet)(payload => read[T](payload))
        }
      }
    }

  protected def readPayloads[T](
      resultSet: ResultSet
  )(decode: String => T): Vector[T] =
    @tailrec
    def loop(acc: Vector[T]): Vector[T] =
      if resultSet.next() then loop(decode(resultSet.getString("payload")) +: acc)
      else acc.reverse

    loop(Vector.empty)

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
