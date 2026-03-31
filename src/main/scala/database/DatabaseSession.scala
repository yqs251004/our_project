package database

import cats.effect.IO
import database.postgres.{DatabaseConfig as PostgresDatabaseConfig, JdbcConnectionFactory, PostgresSchemaInitializer}

object DatabaseSession:

  private def shouldUseTemplateDatabaseVars(
      env: collection.Map[String, String]
  ): Boolean =
    env.keys.exists(_.startsWith("DB_"))

  def normalizedEnvironment(
      env: collection.Map[String, String] = sys.env
  ): collection.Map[String, String] =
    env.get("RIICHI_STORAGE").map(_.trim.toLowerCase) match
      case Some("postgres") =>
        val config = DatabaseConfig.default(env)
        env ++ Map(
          "RIICHI_DB_URL" -> config.url,
          "RIICHI_DB_USER" -> config.user,
          "RIICHI_DB_PASSWORD" -> config.password,
          "RIICHI_DB_SCHEMA" -> config.schema
        )
      case Some(_) =>
        env
      case None if env.contains("RIICHI_DB_URL") =>
        env + ("RIICHI_STORAGE" -> "postgres")
      case None if shouldUseTemplateDatabaseVars(env) =>
        val config = DatabaseConfig.default(env)
        env ++ Map(
          "RIICHI_STORAGE" -> "postgres",
          "RIICHI_DB_URL" -> config.url,
          "RIICHI_DB_USER" -> config.user,
          "RIICHI_DB_PASSWORD" -> config.password,
          "RIICHI_DB_SCHEMA" -> config.schema
        )
      case None =>
        env

  def storageLabel(
      env: collection.Map[String, String] = sys.env
  ): String =
    normalizedEnvironment(env)
      .get("RIICHI_STORAGE")
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .getOrElse("memory")

  def initialize(
      env: collection.Map[String, String] = sys.env
  ): IO[Unit] =
    val normalizedEnv = normalizedEnvironment(env)
    if storageLabel(normalizedEnv) == "postgres" then
      IO.blocking {
        val config = PostgresDatabaseConfig(
          url = normalizedEnv.getOrElse("RIICHI_DB_URL", DatabaseConfig.default(normalizedEnv).url),
          user = normalizedEnv.getOrElse("RIICHI_DB_USER", DatabaseConfig.default(normalizedEnv).user),
          password = normalizedEnv.getOrElse("RIICHI_DB_PASSWORD", DatabaseConfig.default(normalizedEnv).password),
          schema = normalizedEnv.getOrElse("RIICHI_DB_SCHEMA", DatabaseConfig.default(normalizedEnv).schema)
        )
        PostgresSchemaInitializer(JdbcConnectionFactory(config)).initialize()
      }
    else IO.unit

  def applicationContext(
      env: collection.Map[String, String] = sys.env
  ): ApplicationContext =
    ApplicationContext.fromEnvironment(normalizedEnvironment(env))
