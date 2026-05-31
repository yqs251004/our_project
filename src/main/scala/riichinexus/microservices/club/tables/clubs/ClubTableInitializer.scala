package riichinexus.microservices.club.tables.clubs

import java.sql.Connection

object ClubTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists clubs (
      |  id text primary key,
      |  name text not null,
      |  creator_id text not null,
      |  total_points integer not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table clubs add column if not exists name text;
      |alter table clubs add column if not exists creator_id text;
      |alter table clubs add column if not exists total_points integer;
      |alter table clubs add column if not exists payload jsonb;
      |alter table clubs add column if not exists updated_at timestamptz default now();
      |create unique index if not exists idx_clubs_name on clubs (name);
      |create index if not exists idx_clubs_payload_gin on clubs using gin (payload);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
