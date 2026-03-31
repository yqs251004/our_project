package riichinexus

import _root_.database.postgres.*

@main def riichiNexusDbAdmin(command: String = "health"): Unit =
  val config = DatabaseConfig.fromEnv()
  val connectionFactory = JdbcConnectionFactory(config)
  PostgresSchemaInitializer(connectionFactory).initialize()
  val admin = PostgresAdminService(connectionFactory)

  command.trim.toLowerCase match
    case "health" =>
      println(s"database=${config.url}")
      println(s"schema=${config.schema}")
      println(s"schemaVersion=${admin.schemaVersion().getOrElse(0)}")
      println(s"reachable=${admin.ping()}")
    case "stats" =>
      println(s"database=${config.url}")
      println(s"schema=${config.schema}")
      println(s"schemaVersion=${admin.schemaVersion().getOrElse(0)}")
      admin.tableCounts().foreach { case (tableName, rows) =>
        println(s"$tableName=$rows")
      }
    case "reset" =>
      admin.truncateAll()
      println("database reset completed")
    case other =>
      throw IllegalArgumentException(s"Unsupported db admin command: $other")
