package riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask

import java.sql.Connection

object AdvancedStatsRecomputeTaskTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists advanced_stats_recompute_tasks (
      |  id text primary key,
      |  owner_key text not null,
      |  owner_type text not null,
      |  status text not null,
      |  calculator_version integer not null,
      |  requested_at timestamptz not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table advanced_stats_recompute_tasks add column if not exists owner_key text;
      |alter table advanced_stats_recompute_tasks add column if not exists owner_type text;
      |alter table advanced_stats_recompute_tasks add column if not exists status text;
      |alter table advanced_stats_recompute_tasks add column if not exists calculator_version integer;
      |alter table advanced_stats_recompute_tasks add column if not exists requested_at timestamptz;
      |alter table advanced_stats_recompute_tasks add column if not exists payload jsonb;
      |alter table advanced_stats_recompute_tasks add column if not exists updated_at timestamptz default now();
      |create index if not exists idx_advanced_stats_tasks_pending on advanced_stats_recompute_tasks (status, requested_at);
      |create index if not exists idx_advanced_stats_tasks_owner on advanced_stats_recompute_tasks (owner_key, calculator_version, status);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
