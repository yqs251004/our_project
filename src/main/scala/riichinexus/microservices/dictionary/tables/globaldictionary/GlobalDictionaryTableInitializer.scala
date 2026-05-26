package riichinexus.microservices.dictionary.tables.globaldictionary

import java.sql.Connection

object GlobalDictionaryTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists global_dictionary (
      |  key text primary key,
      |  updated_at timestamptz not null,
      |  payload jsonb not null
      |)
      |;
      |alter table global_dictionary add column if not exists updated_at timestamptz;
      |alter table global_dictionary add column if not exists payload jsonb;
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
