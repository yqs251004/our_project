package database.postgres

final case class DatabaseConfig(
    url: String,
    user: String,
    password: String,
    schema: String = "public"
)

object DatabaseConfig:
  def fromEnv(
      env: collection.Map[String, String] = sys.env
  ): DatabaseConfig =
    DatabaseConfig(
      url = env.getOrElse("RIICHI_DB_URL", "jdbc:postgresql://localhost:5432/tongwen"),
      user = env.getOrElse("RIICHI_DB_USER", "db"),
      password = env.getOrElse("RIICHI_DB_PASSWORD", "root"),
      schema = env.getOrElse("RIICHI_DB_SCHEMA", "public")
    )
