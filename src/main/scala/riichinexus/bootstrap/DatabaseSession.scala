package riichinexus.bootstrap

import cats.effect.IO
import riichinexus.infrastructure.postgres.{DatabaseConfig as PostgresDatabaseConfig, JdbcConnectionFactory, PostgresSchemaInitializer}

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
        baseEnv ++ Map(
          "RIICHI_DB_URL" -> baseEnv.getOrElse("RIICHI_DB_URL", TemplateDatabaseConfig.default(baseEnv).url),
          "RIICHI_DB_USER" -> baseEnv.getOrElse("RIICHI_DB_USER", TemplateDatabaseConfig.default(baseEnv).user),
          "RIICHI_DB_PASSWORD" -> baseEnv.getOrElse("RIICHI_DB_PASSWORD", TemplateDatabaseConfig.default(baseEnv).password),
          "RIICHI_DB_SCHEMA" -> baseEnv.getOrElse("RIICHI_DB_SCHEMA", TemplateDatabaseConfig.default(baseEnv).schema)
        )
      case Some(_) =>
        baseEnv
      case None if baseEnv.contains("RIICHI_DB_URL") =>
        baseEnv.updated("RIICHI_STORAGE", "postgres")
      case None if shouldUseTemplateDatabaseVars(baseEnv) =>
        val config = TemplateDatabaseConfig.default(baseEnv)
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

  def initialize(
      env: collection.Map[String, String] = sys.env
  ): IO[Unit] =
    val normalizedEnv = normalizedEnvironment(env)
    if storageLabel(normalizedEnv) == "postgres" then
      IO.blocking {
        val config = PostgresDatabaseConfig(
          url = normalizedEnv.getOrElse("RIICHI_DB_URL", TemplateDatabaseConfig.default(normalizedEnv).url),
          user = normalizedEnv.getOrElse("RIICHI_DB_USER", TemplateDatabaseConfig.default(normalizedEnv).user),
          password = normalizedEnv.getOrElse("RIICHI_DB_PASSWORD", TemplateDatabaseConfig.default(normalizedEnv).password),
          schema = normalizedEnv.getOrElse("RIICHI_DB_SCHEMA", TemplateDatabaseConfig.default(normalizedEnv).schema)
        )
        PostgresSchemaInitializer(JdbcConnectionFactory(config)).initialize()
      }
    else IO.unit

  def applicationContext(
      env: collection.Map[String, String] = sys.env
  ): ApplicationContext =
    ApplicationContext.fromEnvironment(normalizedEnvironment(env))
