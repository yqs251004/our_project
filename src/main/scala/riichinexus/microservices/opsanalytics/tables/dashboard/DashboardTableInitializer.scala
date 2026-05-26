package riichinexus.microservices.opsanalytics.tables.dashboard

import java.sql.Connection

object DashboardTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists dashboards (
      |  owner_key text primary key,
      |  owner_type text not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table dashboards add column if not exists owner_type text;
      |alter table dashboards add column if not exists payload jsonb;
      |alter table dashboards add column if not exists updated_at timestamptz default now();
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
