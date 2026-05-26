package riichinexus.microservices.tournament.tables.matchrecord

import java.sql.Connection

object MatchRecordTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists match_records (
      |  id text primary key,
      |  table_id text not null,
      |  tournament_id text not null,
      |  stage_id text not null,
      |  generated_at timestamptz not null,
      |  player_ids text[] not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table match_records add column if not exists table_id text;
      |alter table match_records add column if not exists tournament_id text;
      |alter table match_records add column if not exists stage_id text;
      |alter table match_records add column if not exists generated_at timestamptz;
      |alter table match_records add column if not exists player_ids text[];
      |alter table match_records add column if not exists payload jsonb;
      |alter table match_records add column if not exists updated_at timestamptz default now();
      |create unique index if not exists idx_match_records_table_id on match_records (table_id);
      |create index if not exists idx_match_records_tournament_id on match_records (tournament_id);
      |create index if not exists idx_match_records_tournament_stage_generated_at on match_records (tournament_id, stage_id, generated_at desc);
      |create index if not exists idx_match_records_player_ids on match_records using gin (player_ids);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
