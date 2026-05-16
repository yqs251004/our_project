package riichinexus.infrastructure.postgres

import java.sql.Connection

import scala.util.Using

final class PostgresSchemaInitializer(connectionFactory: JdbcConnectionFactory):
  def initialize(): Unit =
    connectionFactory.withConnection { connection =>
      PostgresSchemaDefinitions.statements.foreach { sql =>
        execute(connection, sql)
      }
    }

  private def execute(connection: Connection, sql: String): Unit =
    Using.resource(connection.createStatement()) { statement =>
      statement.execute(sql)
    }

object PostgresSchemaInitializer:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresSchemaInitializer =
    new PostgresSchemaInitializer(connectionFactory)
