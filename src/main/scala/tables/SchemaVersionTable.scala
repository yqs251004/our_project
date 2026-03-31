package tables

object SchemaVersionTable:

  val tableName = "schema_version"

  val initTableSql: String =
    """
      |create table if not exists schema_version (
      |  version integer primary key,
      |  description text not null,
      |  applied_at timestamptz not null default now()
      |)
      |""".stripMargin
