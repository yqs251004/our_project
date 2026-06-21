package riichinexus.system.postgres

import cats.effect.{IO, Resource}

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

import scala.util.Using

/** JDBC 连接工厂与事务边界管理器。
  *
  * 工厂负责加载 PostgreSQL 驱动、创建带 schema 的连接，并通过线程本地连接支持同步和 cats-effect 两套事务执行路径。
  */
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

  def withTransactionConnection[A](operation: Connection => IO[A]): IO[A] =
    connectionResource.use { connection =>
      for
        previousAutoCommit <- IO.blocking(connection.getAutoCommit)
        _ <- IO.blocking {
          connection.setAutoCommit(false)
          currentConnection.set(connection)
        }
        result <- operation(connection).attempt
        _ <- result match
          case Right(_) => IO.blocking(connection.commit())
          case Left(_)  => IO.blocking(connection.rollback()).handleErrorWith(_ => IO.unit)
        _ <- IO.blocking {
          currentConnection.remove()
          connection.setAutoCommit(previousAutoCommit)
        }
        value <- IO.fromEither(result)
      yield value
    }

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
      Using.resource(newConnection())(f)
    catch
      case error: SQLException if error.getMessage != null && error.getMessage.contains("No suitable driver") =>
        throw IllegalStateException(
          "PostgreSQL JDBC driver is not available. Run sbt once to download dependencies.",
          error
        )

  private def connectionResource: Resource[IO, Connection] =
    Resource.make(IO.blocking(newConnection())) { connection =>
      IO.blocking(connection.close()).handleErrorWith(_ => IO.unit)
    }

  private def newConnection(): Connection =
    val connection = DriverManager.getConnection(config.url, config.user, config.password)
    connection.setSchema(config.schema)
    connection

object JdbcConnectionFactory:
  def apply(config: DatabaseConfig): JdbcConnectionFactory =
    new JdbcConnectionFactory(config)
