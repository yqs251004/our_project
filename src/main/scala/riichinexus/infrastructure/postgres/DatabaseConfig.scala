package riichinexus.infrastructure.postgres

type DatabaseConfig = _root_.database.postgres.DatabaseConfig

object DatabaseConfig:
  def apply(
      url: String,
      user: String,
      password: String,
      schema: String = "public"
  ): DatabaseConfig =
    _root_.database.postgres.DatabaseConfig(url, user, password, schema)

  def fromEnv(
      env: collection.Map[String, String] = sys.env
  ): DatabaseConfig =
    _root_.database.postgres.DatabaseConfig.fromEnv(env)
