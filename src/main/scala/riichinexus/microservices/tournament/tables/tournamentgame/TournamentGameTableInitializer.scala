package riichinexus.microservices.tournament.tables.tournamentgame

import java.sql.Connection

object TournamentGameTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists tables (
      |  id text primary key,
      |  tournament_id text not null,
      |  stage_id text not null,
      |  table_no integer not null,
      |  status text not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table tables add column if not exists tournament_id text;
      |alter table tables add column if not exists stage_id text;
      |alter table tables add column if not exists table_no integer;
      |alter table tables add column if not exists status text;
      |alter table tables add column if not exists payload jsonb;
      |alter table tables add column if not exists updated_at timestamptz default now();
      |create unique index if not exists idx_tables_stage_table_no on tables (tournament_id, stage_id, table_no);
      |create index if not exists idx_tables_tournament_stage on tables (tournament_id, stage_id);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
