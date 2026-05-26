package riichinexus.infrastructure.postgres

private[postgres] object PostgresSchemaDefinitions:
  private final case class TableSchema(
      createSql: String,
      migrations: Vector[String] = Vector.empty,
      indexes: Vector[String] = Vector.empty
  ):
    def statements: Vector[String] =
      Vector(createSql) ++ migrations ++ indexes

  private val coreSchema: Vector[String] =
    Vector(
      TableSchema(
        createSql =
          """
            |create table if not exists schema_version (
            |  version integer primary key,
            |  description text not null,
            |  applied_at timestamptz not null default now()
            |)
            |""".stripMargin
      )
    ).flatMap(_.statements)

  private val schemaVersionMarkers: Vector[String] =
    Vector(
      1 -> "Initial RiichiNexus PostgreSQL schema",
      2 -> "Extended RiichiNexus tournament workflow schema",
      3 -> "Added settlement persistence and audit event schema",
      4 -> "Added guest session persistence schema",
      5 -> "Added advanced stats board persistence schema",
      6 -> "Added advanced stats recompute task pipeline schema",
      7 -> "Added event cascade record subscriber schema",
      8 -> "Added dictionary namespace governance schema",
      9 -> "Added durable domain event outbox schema",
      10 -> "Added domain event delivery receipt schema",
      11 -> "Added subscriber ordering cursor and partitioned outbox schema",
      12 -> "Added account credential and authenticated session schema"
    ).map { case (version, description) =>
      s"""
         |insert into schema_version(version, description)
         |values ($version, '$description')
         |on conflict (version) do nothing
         |""".stripMargin
    }

  val statements: Vector[String] =
    Vector(
      coreSchema,
      schemaVersionMarkers
    ).flatten
