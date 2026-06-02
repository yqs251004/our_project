package riichinexus.system

import cats.effect.IO
import riichinexus.system.postgres.{
  DatabaseConfig as PostgresDatabaseConfig,
  JdbcConnectionFactory,
  PostgresSchemaInitializer
}

object DatabaseSession:

  private def shouldUseTemplateDatabaseVars(
      env: collection.Map[String, String]
  ): Boolean =
    env.keys.exists(_.startsWith("DB_"))

  def normalizedEnvironment(
      env: collection.Map[String, String] = sys.env
  ): collection.immutable.Map[String, String] =
    val baseEnv = Map.from(env)
    baseEnv.get("RIICHI_STORAGE").map(_.trim.toLowerCase) match
      case Some("postgres") =>
        val config = DatabaseConfig.default(baseEnv)
        baseEnv ++ Map(
          "RIICHI_DB_URL" -> baseEnv.getOrElse("RIICHI_DB_URL", config.url),
          "RIICHI_DB_USER" -> baseEnv.getOrElse("RIICHI_DB_USER", config.user),
          "RIICHI_DB_PASSWORD" -> baseEnv.getOrElse("RIICHI_DB_PASSWORD", config.password),
          "RIICHI_DB_SCHEMA" -> baseEnv.getOrElse("RIICHI_DB_SCHEMA", config.schema)
        )
      case Some(_) =>
        baseEnv
      case None if baseEnv.contains("RIICHI_DB_URL") =>
        baseEnv.updated("RIICHI_STORAGE", "postgres")
      case None if shouldUseTemplateDatabaseVars(baseEnv) =>
        val config = DatabaseConfig.default(baseEnv)
        baseEnv ++ Map(
          "RIICHI_STORAGE" -> "postgres",
          "RIICHI_DB_URL" -> config.url,
          "RIICHI_DB_USER" -> config.user,
          "RIICHI_DB_PASSWORD" -> config.password,
          "RIICHI_DB_SCHEMA" -> config.schema
        )
      case None =>
        baseEnv

  def storageLabel(
      env: collection.Map[String, String] = sys.env
  ): String =
    normalizedEnvironment(env)
      .get("RIICHI_STORAGE")
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .getOrElse("memory")

  def connectionFactory(
      env: collection.Map[String, String] = sys.env
  ): JdbcConnectionFactory =
    val normalizedEnv = normalizedEnvironment(env)
    if storageLabel(normalizedEnv) != "postgres" then
      throw IllegalStateException("In-memory storage is no longer supported; configure PostgreSQL storage")
    JdbcConnectionFactory(postgresConfig(normalizedEnv))

  def initialize(connectionFactory: JdbcConnectionFactory): IO[Unit] =
    IO.blocking(PostgresSchemaInitializer(connectionFactory).initialize())

  def initialize(
      env: collection.Map[String, String] = sys.env
  ): IO[Unit] =
    initialize(connectionFactory(normalizedEnvironment(env)))

  private def postgresConfig(
      env: collection.Map[String, String]
  ): PostgresDatabaseConfig =
    val normalizedEnv = normalizedEnvironment(env)
    val config = DatabaseConfig.default(normalizedEnv)
    PostgresDatabaseConfig(
      url = normalizedEnv.getOrElse("RIICHI_DB_URL", config.url),
      user = normalizedEnv.getOrElse("RIICHI_DB_USER", config.user),
      password = normalizedEnv.getOrElse("RIICHI_DB_PASSWORD", config.password),
      schema = normalizedEnv.getOrElse("RIICHI_DB_SCHEMA", config.schema)
    )
