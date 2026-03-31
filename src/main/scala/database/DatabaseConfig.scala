package database

import java.nio.file.Paths

final case class DatabaseConfig(
    host: String,
    port: Int,
    databaseName: String,
    user: String,
    password: String,
    schema: String,
    maxPoolSize: Int,
    connectionTimeoutMs: Long
):
  def url: String =
    s"jdbc:postgresql://$host:$port/$databaseName"

object DatabaseConfig:

  private def inferredDatabaseName: String =
    Option(Paths.get(sys.props.getOrElse("user.dir", ".")).getFileName)
      .map(_.toString.replace('-', '_'))
      .filter(_.nonEmpty)
      .getOrElse("riichi_nexus")

  def default(env: collection.Map[String, String] = sys.env): DatabaseConfig =
    DatabaseConfig(
      host = env.get("DB_HOST").orElse(env.get("RIICHI_DB_HOST")).getOrElse("127.0.0.1"),
      port = env.get("DB_PORT").orElse(env.get("RIICHI_DB_PORT")).flatMap(_.toIntOption).getOrElse(5432),
      databaseName = env.get("DB_NAME").orElse(env.get("RIICHI_DB_NAME")).getOrElse(inferredDatabaseName),
      user = env.get("DB_USER").orElse(env.get("RIICHI_DB_USER")).getOrElse("db"),
      password = env.get("DB_PASSWORD").orElse(env.get("RIICHI_DB_PASSWORD")).getOrElse("root"),
      schema = env.get("DB_SCHEMA").orElse(env.get("RIICHI_DB_SCHEMA")).getOrElse("public"),
      maxPoolSize = env.get("DB_MAX_POOL_SIZE").flatMap(_.toIntOption).getOrElse(10),
      connectionTimeoutMs = env.get("DB_CONNECTION_TIMEOUT_MS").flatMap(_.toLongOption).getOrElse(3000L)
    )
