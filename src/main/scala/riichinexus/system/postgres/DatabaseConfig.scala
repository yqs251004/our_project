package riichinexus.system.postgres

/** JDBC 连接所需的 PostgreSQL 配置。
  *
  * 该配置从环境变量解析出连接 URL、用户名、密码和 schema，供 `JdbcConnectionFactory` 创建实际数据库连接。
  */
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
