package riichinexus.microservices.tournament.appeal.tables.appealticket

import java.sql.Connection

object AppealTicketTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists appeal_tickets (
      |  id text primary key,
      |  table_id text not null,
      |  tournament_id text not null,
      |  stage_id text not null,
      |  status text not null,
      |  opened_by text not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table appeal_tickets add column if not exists table_id text;
      |alter table appeal_tickets add column if not exists tournament_id text;
      |alter table appeal_tickets add column if not exists stage_id text;
      |alter table appeal_tickets add column if not exists status text;
      |alter table appeal_tickets add column if not exists opened_by text;
      |alter table appeal_tickets add column if not exists payload jsonb;
      |alter table appeal_tickets add column if not exists updated_at timestamptz default now();
      |create index if not exists idx_appeals_tournament_id on appeal_tickets (tournament_id);
      |create index if not exists idx_appeals_table_id on appeal_tickets (table_id);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
