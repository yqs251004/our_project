package riichinexus.infrastructure.postgres

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

import scala.util.Using

import riichinexus.application.ports.TransactionManager

final class JdbcConnectionFactory(config: DatabaseConfig):
  private val driverClass = "org.postgresql.Driver"
  private val currentConnection = ThreadLocal[Connection]()

  try Class.forName(driverClass)
  catch
    case _: ClassNotFoundException =>
      ()

  def withConnection[A](f: Connection => A): A =
    Option(currentConnection.get()) match
      case Some(connection) =>
        f(connection)
      case None =>
        openConnection(f)

  def inTransaction[A](operation: => A): A =
    Option(currentConnection.get()) match
      case Some(_) =>
        operation
      case None =>
        openConnection { connection =>
          val previousAutoCommit = connection.getAutoCommit
          connection.setAutoCommit(false)
          currentConnection.set(connection)
          try
            val result = operation
            connection.commit()
            result
          catch
            case error: Throwable =>
              connection.rollback()
              throw error
          finally
            currentConnection.remove()
            connection.setAutoCommit(previousAutoCommit)
        }

  private def openConnection[A](f: Connection => A): A =
    try
      Using.resource(DriverManager.getConnection(config.url, config.user, config.password)) { connection =>
        connection.setSchema(config.schema)
        f(connection)
      }
    catch
      case error: SQLException if error.getMessage != null && error.getMessage.contains("No suitable driver") =>
        throw IllegalStateException(
          "PostgreSQL JDBC driver is not available. Run sbt once to download dependencies.",
          error
        )

object JdbcConnectionFactory:
  def apply(config: DatabaseConfig): JdbcConnectionFactory =
    new JdbcConnectionFactory(config)

final class JdbcTransactionManager(
    connectionFactory: JdbcConnectionFactory
) extends TransactionManager:
  override def inTransaction[A](operation: => A): A =
    connectionFactory.inTransaction(operation)

object JdbcTransactionManager:
  def apply(connectionFactory: JdbcConnectionFactory): JdbcTransactionManager =
    new JdbcTransactionManager(connectionFactory)
