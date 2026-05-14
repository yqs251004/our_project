package riichinexus.infrastructure.postgres

import java.sql.Connection

import scala.util.Using

final class PostgresAdminService(connectionFactory: JdbcConnectionFactory):
  private val managedTables =
    Vector(
      "players",
      "account_credentials",
      "authenticated_sessions",
      "guest_sessions",
      "clubs",
      "tournaments",
      "tables",
      "match_records",
      "paifus",
      "appeal_tickets",
      "dashboards",
      "advanced_stats_boards",
      "advanced_stats_recompute_tasks",
      "global_dictionary",
      "dictionary_namespaces",
      "tournament_settlements",
      "event_cascade_records",
      "domain_event_outbox",
      "domain_event_delivery_receipts",
      "domain_event_subscriber_cursors",
      "audit_events"
    )

  def ping(): Boolean =
    connectionFactory.withConnection { connection =>
      Using.resource(connection.prepareStatement("select 1")) { statement =>
        Using.resource(statement.executeQuery())(_.next())
      }
    }

  def schemaVersion(): Option[Int] =
    connectionFactory.withConnection { connection =>
      Using.resource(connection.prepareStatement("select max(version) as version from schema_version")) {
        statement =>
          Using.resource(statement.executeQuery()) { resultSet =>
            if resultSet.next() then Option(resultSet.getObject("version")).map(_ => resultSet.getInt("version"))
            else None
          }
      }
    }

  def tableCounts(): Vector[(String, Int)] =
    managedTables.map { tableName =>
      tableName -> countRows(tableName)
    }

  def truncateAll(): Unit =
    connectionFactory.inTransaction {
      connectionFactory.withConnection { connection =>
        executeAdmin(connection, "truncate table audit_events restart identity")
        executeAdmin(connection, "truncate table domain_event_subscriber_cursors restart identity")
        executeAdmin(connection, "truncate table domain_event_delivery_receipts restart identity")
        executeAdmin(connection, "truncate table domain_event_outbox restart identity")
        executeAdmin(connection, "select setval('domain_event_outbox_sequence', 1, false)")
        executeAdmin(connection, "truncate table tournament_settlements restart identity")
        executeAdmin(connection, "truncate table event_cascade_records restart identity")
        executeAdmin(connection, "truncate table global_dictionary restart identity")
        executeAdmin(connection, "truncate table dictionary_namespaces restart identity")
        executeAdmin(connection, "truncate table advanced_stats_recompute_tasks restart identity")
        executeAdmin(connection, "truncate table dashboards restart identity")
        executeAdmin(connection, "truncate table advanced_stats_boards restart identity")
        executeAdmin(connection, "truncate table authenticated_sessions restart identity")
        executeAdmin(connection, "truncate table account_credentials restart identity")
        executeAdmin(connection, "truncate table guest_sessions restart identity")
        executeAdmin(connection, "truncate table appeal_tickets restart identity")
        executeAdmin(connection, "truncate table paifus restart identity")
        executeAdmin(connection, "truncate table match_records restart identity")
        executeAdmin(connection, "truncate table tables restart identity")
        executeAdmin(connection, "truncate table tournaments restart identity")
        executeAdmin(connection, "truncate table clubs restart identity")
        executeAdmin(connection, "truncate table players restart identity")
      }
    }

  private def executeAdmin(connection: Connection, sql: String): Unit =
    Using.resource(connection.createStatement())(_.execute(sql))

  private def countRows(tableName: String): Int =
    connectionFactory.withConnection { connection =>
      Using.resource(connection.prepareStatement(s"select count(*) as row_count from $tableName")) { statement =>
        Using.resource(statement.executeQuery()) { resultSet =>
          if resultSet.next() then resultSet.getInt("row_count") else 0
        }
      }
    }

object PostgresAdminService:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAdminService =
    new PostgresAdminService(connectionFactory)
