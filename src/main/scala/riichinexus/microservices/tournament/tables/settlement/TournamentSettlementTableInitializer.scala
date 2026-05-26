package riichinexus.microservices.tournament.tables.settlement

import java.sql.Connection

object TournamentSettlementTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists tournament_settlements (
      |  id text primary key,
      |  tournament_id text not null,
      |  stage_id text not null,
      |  generated_at timestamptz not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table tournament_settlements add column if not exists tournament_id text;
      |alter table tournament_settlements add column if not exists stage_id text;
      |alter table tournament_settlements add column if not exists generated_at timestamptz;
      |alter table tournament_settlements add column if not exists payload jsonb;
      |alter table tournament_settlements add column if not exists updated_at timestamptz default now();
      |create unique index if not exists idx_tournament_settlements_scope on tournament_settlements (tournament_id, stage_id);
      |create index if not exists idx_tournament_settlements_generated_at on tournament_settlements (generated_at);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
