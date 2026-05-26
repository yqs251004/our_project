package riichinexus.microservices.tournament.tables.tournament

import java.sql.Connection

object TournamentTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists tournaments (
      |  id text primary key,
      |  name text not null,
      |  organizer text not null,
      |  status text not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table tournaments add column if not exists name text;
      |alter table tournaments add column if not exists organizer text;
      |alter table tournaments add column if not exists status text;
      |alter table tournaments add column if not exists payload jsonb;
      |alter table tournaments add column if not exists updated_at timestamptz default now();
      |create unique index if not exists idx_tournaments_name_start on tournaments (name, organizer);
      |create index if not exists idx_tournaments_payload_gin on tournaments using gin (payload);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
