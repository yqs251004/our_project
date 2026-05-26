package riichinexus.microservices.tournament.tables.paifu

import java.sql.Connection

object PaifuTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists paifus (
      |  id text primary key,
      |  table_id text not null,
      |  tournament_id text not null,
      |  stage_id text not null,
      |  recorded_at timestamptz not null,
      |  player_ids text[] not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table paifus add column if not exists table_id text;
      |alter table paifus add column if not exists tournament_id text;
      |alter table paifus add column if not exists stage_id text;
      |alter table paifus add column if not exists recorded_at timestamptz;
      |alter table paifus add column if not exists player_ids text[];
      |alter table paifus add column if not exists payload jsonb;
      |alter table paifus add column if not exists updated_at timestamptz default now();
      |create index if not exists idx_paifus_table_id on paifus (table_id);
      |create index if not exists idx_paifus_recorded_at on paifus (recorded_at);
      |create index if not exists idx_paifus_player_ids on paifus using gin (player_ids);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
